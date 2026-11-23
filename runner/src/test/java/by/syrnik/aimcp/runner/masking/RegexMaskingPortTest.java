package by.syrnik.aimcp.runner.masking;

import by.syrnik.aimcp.core.masking.MaskingException;
import by.syrnik.aimcp.core.profile.DataPolicy;
import by.syrnik.aimcp.runner.profile.YamlProjectProfileSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RegexMaskingPortTest {

    private static final Duration BUDGET = Duration.ofSeconds(5);

    @Test
    void projectPatternReplacesTheWholeMatchUnderItsNumber() {
        RegexMaskingPort port = withProjectPatterns("ORD-\\d+");

        assertThat(port.mask("order ORD-123 and ORD-45")).isEqualTo("order [MASKED:project-1] and [MASKED:project-1]");
    }

    @Test
    void projectPatternsAreNumberedInProfileOrder() {
        RegexMaskingPort port = withProjectPatterns("aaa", "bbb");

        assertThat(port.mask("aaa bbb")).isEqualTo("[MASKED:project-1] [MASKED:project-2]");
    }

    @Test
    void groupSecretReplacesOnlyTheGroup() {
        RegexMaskingPort port = withProjectPatterns("user=(?<secret>\\w+)");

        assertThat(port.mask("user=bob x")).isEqualTo("user=[MASKED:project-1] x");
    }

    @Test
    void matchWhereGroupSecretDidNotTakePartMasksNothing() {
        RegexMaskingPort port = withProjectPatterns("(?:a|(?<secret>b))");

        assertThat(port.mask("a b")).isEqualTo("a [MASKED:project-1]");
    }

    @Test
    void emptyMatchesAreIgnored() {
        assertThat(withProjectPatterns("x*").mask("abc")).isEqualTo("abc");
    }

    @Test
    void baseRulesRunBeforeProjectPatterns() {
        RegexMaskingPort port = withProjectPatterns("abc123");

        assertThat(port.mask("password=abc123")).isEqualTo("password=[MASKED:secret-keyword]");
        assertThat(port.mask("note abc123")).isEqualTo("note [MASKED:project-1]");
    }

    @Test
    void eachRuleIsAppliedToTheResultOfThePreviousOne() {
        MaskingRule first = new MaskingRule("first", Pattern.compile("ab"));
        MaskingRule second = new MaskingRule("second", Pattern.compile("b"));

        assertThat(new RegexMaskingPort(List.of(first, second), BUDGET).mask("ab")).isEqualTo("[MASKED:first]");
        assertThat(new RegexMaskingPort(List.of(second, first), BUDGET).mask("ab")).isEqualTo("a[MASKED:second]");
    }

    @Test
    void emptyTextStaysEmptyAndNullIsRejected() {
        RegexMaskingPort port = withProjectPatterns("x");

        assertThat(port.mask("")).isEmpty();
        assertThatThrownBy(() -> port.mask(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void expiredBudgetFailsClosedWithoutQuotingTheText() {
        RegexMaskingPort port = new RegexMaskingPort(
                List.of(new MaskingRule("evil", Pattern.compile("(.*a){25}"))), Duration.ofMillis(200));
        String text = "SECRET123 " + "a".repeat(45) + "!";

        long started = System.nanoTime();
        assertThatThrownBy(() -> port.mask(text))
                .isInstanceOf(MaskingException.class)
                .hasMessageContaining("бюджет времени")
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("SECRET123"));

        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(3));
    }

    @Test
    void budgetIsEnforcedEvenOnShortText() {
        RegexMaskingPort port = new RegexMaskingPort(BaseMaskingRules.rules(), Duration.ofNanos(1));

        assertThatThrownBy(() -> port.mask("password=abc"))
                .isInstanceOf(MaskingException.class)
                .hasMessageContaining("бюджет времени");
    }

    @Test
    void tooDeepRecursionOfAnExpressionFailsClosed() {
        RegexMaskingPort port = withProjectPatterns("(a|b)*c");

        assertThatThrownBy(() -> port.mask("a".repeat(200_000)))
                .isInstanceOf(MaskingException.class)
                .hasMessageContaining("рекурсия");
    }

    @Test
    void overlappingSecretGroupsFailClosed() {
        // группы secret соседних совпадений пересекаются (0-3 и 1-4): результат не определён, текст не отдаём
        RegexMaskingPort port = withProjectPatterns("(?=(?<secret>[abc]{3}))[abc]");

        assertThatThrownBy(() -> port.mask("abcabc"))
                .isInstanceOf(MaskingException.class)
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("abcabc"));
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void budgetMustBePositive(long millis) {
        assertThatThrownBy(() -> new RegexMaskingPort(List.of(), Duration.ofMillis(millis)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void budgetMustNotBeNullOrLongerThanAnHour() {
        assertThatThrownBy(() -> new RegexMaskingPort(List.of(), null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new RegexMaskingPort(List.of(), Duration.ofHours(1).plusMillis(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new RegexMaskingPort(List.of(), Duration.ofHours(1)).mask("x")).isEqualTo("x");
    }

    @Test
    void rulesListIsCopiedOnConstruction() {
        List<MaskingRule> rules = new ArrayList<>();
        RegexMaskingPort port = new RegexMaskingPort(rules, BUDGET);

        rules.add(new MaskingRule("late", Pattern.compile("x")));

        assertThat(port.mask("x")).isEqualTo("x");
    }

    @Test
    void badProjectPatternIsReportedByNumberWithoutQuotingIt() {
        assertThatThrownBy(() -> withProjectPatterns("ok", "(SECRET123"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("project-2")
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("SECRET123"));
    }

    @Test
    void ruleNeedsNameAndPattern() {
        assertThatThrownBy(() -> new MaskingRule(" ", Pattern.compile("x"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MaskingRule(null, Pattern.compile("x"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MaskingRule("x", null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void patternsOfTheContractExampleProfileWorkAfterBaseRules() {
        DataPolicy policy = new YamlProjectProfileSource(Path.of("..", "docs", "examples", "at-profile.yaml")).load().dataPolicy();
        RegexMaskingPort port = RegexMaskingPort.forPolicy(policy, BUDGET);

        assertThat(port.mask("Bearer abc.def-123 and card 1234567890123456"))
                .isEqualTo("Bearer [MASKED:bearer] and card [MASKED:project-2]");
    }

    @Test
    void severalThreadsGetTheSameResults() throws Exception {
        RegexMaskingPort port = withProjectPatterns("ORD-\\d+");
        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int t = 0; t < 8; t++) {
            int thread = t;
            results.add(pool.submit(() -> {
                for (int i = 0; i < 300; i++) {
                    String text = "t" + thread + " password=p" + i + " ORD-" + i;
                    String expected = "t" + thread + " password=[MASKED:secret-keyword] [MASKED:project-1]";
                    if (!port.mask(text).equals(expected)) {
                        return false;
                    }
                }
                return true;
            }));
        }
        for (Future<Boolean> result : results) {
            assertThat(result.get()).isTrue();
        }
        pool.shutdown();
    }

    private static RegexMaskingPort withProjectPatterns(String... patterns) {
        return RegexMaskingPort.forPolicy(new DataPolicy(List.of(patterns), List.of()), BUDGET);
    }
}
