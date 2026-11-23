package by.syrnik.aimcp.runner.profile;

import by.syrnik.aimcp.core.profile.DataPolicy;
import by.syrnik.aimcp.core.profile.Env;
import by.syrnik.aimcp.core.profile.InvalidProfileException;
import by.syrnik.aimcp.core.profile.ProjectProfile;
import by.syrnik.aimcp.core.profile.Run;
import by.syrnik.aimcp.core.profile.Testit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Разбор профиля по контракту docs/project-profile.md: пример из документа, значения по умолчанию, каждое правило
 * (путь к полю в сообщении), сообщения без значений, файл.
 */
class YamlProjectProfileSourceTest {

    private static final String REPO = "https://ahcode.example.invalid/group/demo.git";
    private static final String MINIMAL = """
            profile_version: 1
            project: demo
            repo: https://ahcode.example.invalid/group/demo.git
            run:
              command: [gradle, test]
            env:
              restricted: false
            """;

    @Test
    void exampleFromTheContractIsLoaded() {
        Path example = Path.of("..", "docs", "examples", "at-profile.yaml");

        ProjectProfile profile = new YamlProjectProfileSource(example).load();

        assertThat(profile).isEqualTo(new ProjectProfile("demo-is", "https://ahcode.example.invalid/group/demo-tests.git", "2.4.1",
                new Testit("00000000-0000-0000-0000-000000000000", "1.2.8"),
                new Run(List.of("gradle", "testInTest", "-Pstand=test"), Duration.ofMinutes(45)),
                new Env(false),
                new DataPolicy(List.of("Bearer\\s+[A-Za-z0-9._~+/=-]+", "\\b\\d{16}\\b"), List.of("secrets/**", "**/*.pem")),
                List.of("team-qa-demo")));
    }

    @Test
    void absentOptionalPartsGetDefaults() {
        ProjectProfile profile = YamlProjectProfileSource.parse(MINIMAL);

        assertThat(profile).isEqualTo(new ProjectProfile("demo", REPO, null, new Testit(null, null),
                new Run(List.of("gradle", "test"), Duration.ofHours(1)), new Env(false),
                new DataPolicy(List.of(), List.of()), List.of()));
    }

    @Test
    void restrictedTrueIsRead() {
        assertThat(YamlProjectProfileSource.parse(MINIMAL.replace("restricted: false", "restricted: true")).env().restricted())
                .isTrue();
    }

    @Test
    void boundaryValuesAreAccepted() {
        String yaml = MINIMAL
                .replace("project: demo", "project: " + "p".repeat(100))
                .replace("  command: [gradle, test]\n", "  command: [gradle, test]\n  timeout: PT12H\n")
                + "data_policy:\n  mask: " + items(100) + "\n  never_send: " + items(100) + "\n";

        ProjectProfile profile = YamlProjectProfileSource.parse(yaml);

        assertThat(profile.project()).hasSize(100);
        assertThat(profile.run().timeout()).isEqualTo(Duration.ofHours(12));
        assertThat(profile.dataPolicy().mask()).hasSize(100);
        assertThat(profile.dataPolicy().neverSend()).hasSize(100);
    }

    @Test
    void maskOfMaximumLengthIsAccepted() {
        String yaml = MINIMAL + "data_policy:\n  mask: ['" + "a".repeat(500) + "']\n";

        assertThat(YamlProjectProfileSource.parse(yaml).dataPolicy().mask()).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"git@ahcode.example.invalid:group/demo.git", "ssh://git@ahcode.example.invalid/group/demo.git",
            "http://ahcode.example.invalid/demo.git"})
    void repoLinksWithoutLoginAndPasswordInHttpAreAccepted(String repo) {
        assertThat(YamlProjectProfileSource.parse(MINIMAL.replace(REPO, repo)).repo()).isEqualTo(repo);
    }

    @Test
    void byteOrderMarkIsIgnored() {
        assertThat(YamlProjectProfileSource.parse("\uFEFF" + MINIMAL).project()).isEqualTo("demo");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidProfiles")
    void invalidProfileIsRejectedWithFieldPath(String title, String yaml, String messageStart) {
        assertThatThrownBy(() -> YamlProjectProfileSource.parse(yaml))
                .isInstanceOf(InvalidProfileException.class)
                .satisfies(e -> assertThat(e.getMessage()).startsWith(messageStart));
    }

    static Stream<Arguments> invalidProfiles() {
        String m = MINIMAL;
        return Stream.of(
                // файл и YAML
                bad("пустой файл", "", "/:"),
                bad("корень — список", "- a", "/:"),
                bad("ключ корня — булево (on:)", "on: 1", "/:"),
                bad("синтаксическая ошибка", "run: [", "YAML не разобран"),
                bad("повторяющийся ключ", m + "project: other\n", "YAML не разобран"),
                bad("тег !!", m.replace("project: demo", "project: !!java.net.URL \"http://x\""), "YAML не разобран"),
                bad("слишком глубокая вложенность", "[".repeat(30_000) + "]".repeat(30_000), "YAML не разобран"),
                // версия проверяется раньше остальных правил
                bad("нет версии", m.replace("profile_version: 1\n", ""), "/profile_version:"),
                bad("версия строкой", m.replace("profile_version: 1", "profile_version: \"1\""), "/profile_version:"),
                bad("версия дробным", m.replace("profile_version: 1", "profile_version: 1.0"), "/profile_version:"),
                bad("версия 2", m.replace("profile_version: 1", "profile_version: 2"), "/profile_version:"),
                bad("версия 2 и новый ключ — версия первой", m.replace("profile_version: 1", "profile_version: 2") + "future: 1\n", "/profile_version:"),
                // неизвестные ключи
                bad("неизвестный ключ корня", m + "data_polcy: {}\n", "/data_polcy:"),
                bad("run.suites убран из контракта", m.replace("  command: [gradle, test]\n", "  command: [gradle, test]\n  suites: [smoke]\n"), "/run/suites:"),
                bad("env.stands убран из контракта", m.replace("restricted: false", "restricted: false\n  stands: {test: http://x}"), "/env/stands:"),
                bad("неизвестный ключ testit", m + "testit:\n  projectid: x\n", "/testit/projectid:"),
                bad("опечатка в data_policy", m + "data_policy:\n  nevr_send: []\n", "/data_policy/nevr_send:"),
                // project, repo, framework_ver
                bad("нет project", m.replace("project: demo\n", ""), "/project:"),
                bad("project пустой", m.replace("project: demo", "project: \"\""), "/project:"),
                bad("project числом", m.replace("project: demo", "project: 5"), "/project:"),
                bad("project с пробелом", m.replace("project: demo", "project: bad name"), "/project:"),
                bad("project длиннее 100", m.replace("project: demo", "project: " + "p".repeat(101)), "/project:"),
                bad("нет repo", m.replace("repo: " + REPO + "\n", ""), "/repo:"),
                bad("repo с логином и паролем", m.replace(REPO, "https://user:pass@ahcode.example.invalid/g.git"), "/repo:"),
                bad("repo с логином", m.replace(REPO, "https://user@ahcode.example.invalid/g.git"), "/repo:"),
                bad("repo не адрес", m.replace(REPO, "https://exa mple/x.git"), "/repo:"),
                bad("framework_ver числом (1.10)", m + "framework_ver: 1.10\n", "/framework_ver:"),
                // testit
                bad("testit — не объект", m + "testit: x\n", "/testit:"),
                bad("testit без значения", m + "testit:\n", "/testit:"),
                bad("testit.project_id числом", m + "testit:\n  project_id: 5\n", "/testit/project_id:"),
                // run
                bad("нет run", m.replace("run:\n  command: [gradle, test]\n", ""), "/run:"),
                bad("run без значения", m.replace("  command: [gradle, test]\n", ""), "/run:"),
                bad("нет command", m.replace("  command: [gradle, test]\n", "  timeout: PT1H\n"), "/run/command:"),
                bad("command пустой", m.replace("[gradle, test]", "[]"), "/run/command:"),
                bad("command строкой", m.replace("[gradle, test]", "gradle test"), "/run/command:"),
                bad("command с пустым элементом", m.replace("[gradle, test]", "[gradle, \"\"]"), "/run/command/1:"),
                bad("command с числом", m.replace("[gradle, test]", "[gradle, 1]"), "/run/command/1:"),
                bad("command с no (булево)", m.replace("[gradle, test]", "[gradle, no]"), "/run/command/1:"),
                bad("timeout числом", withTimeout(m, "45"), "/run/timeout:"),
                bad("timeout 45m", withTimeout(m, "45m"), "/run/timeout:"),
                bad("timeout ноль", withTimeout(m, "PT0S"), "/run/timeout:"),
                bad("timeout отрицательный", withTimeout(m, "-PT5M"), "/run/timeout:"),
                bad("timeout больше 12 часов", withTimeout(m, "PT12H1S"), "/run/timeout:"),
                // env
                bad("нет env", m.replace("env:\n  restricted: false\n", ""), "/env:"),
                bad("нет restricted", m.replace("env:\n  restricted: false\n", "env: {}\n"), "/env/restricted:"),
                bad("restricted строкой", m.replace("restricted: false", "restricted: \"true\""), "/env/restricted:"),
                bad("restricted числом", m.replace("restricted: false", "restricted: 1"), "/env/restricted:"),
                bad("restricted без значения", m.replace("restricted: false", "restricted:"), "/env/restricted:"),
                // data_policy
                bad("data_policy — список", m + "data_policy: []\n", "/data_policy:"),
                bad("mask строкой", m + "data_policy:\n  mask: abc\n", "/data_policy/mask:"),
                bad("mask без значения", m + "data_policy:\n  mask:\n", "/data_policy/mask:"),
                bad("mask с числом", m + "data_policy:\n  mask: [abc, 5]\n", "/data_policy/mask/1:"),
                bad("mask: не регулярное выражение", m + "data_policy:\n  mask: ['(']\n", "/data_policy/mask/0:"),
                bad("mask: .* находит пустую строку", m + "data_policy:\n  mask: ['.*']\n", "/data_policy/mask/0:"),
                bad("mask: a* находит пустую строку", m + "data_policy:\n  mask: ['a*']\n", "/data_policy/mask/0:"),
                bad("mask длиннее 500", m + "data_policy:\n  mask: ['" + "a".repeat(501) + "']\n", "/data_policy/mask/0:"),
                bad("mask: 101 шаблон", m + "data_policy:\n  mask: " + items(101) + "\n", "/data_policy/mask:"),
                bad("never_send: абсолютный путь", neverSend("'/etc/**'"), "/data_policy/never_send/0:"),
                bad("never_send: ..", neverSend("'../secrets'"), "/data_policy/never_send/0:"),
                bad("never_send: .. внутри", neverSend("'a/../b'"), "/data_policy/never_send/0:"),
                bad("never_send: обратная косая", neverSend("'a\\b'"), "/data_policy/never_send/0:"),
                bad("never_send: диск", neverSend("'C:/x'"), "/data_policy/never_send/0:"),
                bad("never_send: не glob", neverSend("'{a'"), "/data_policy/never_send/0:"),
                bad("never_send: 101 шаблон", m + "data_policy:\n  never_send: " + items(101) + "\n", "/data_policy/never_send:"),
                // owners
                bad("owners с no (булево)", m + "owners: [no]\n", "/owners/0:"),
                bad("owners с пустой строкой", m + "owners: ['']\n", "/owners/0:"),
                bad("owners без значения", m + "owners:\n", "/owners:"),
                bad("owners строкой", m + "owners: abc\n", "/owners:"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("profilesWithSecrets")
    void messagesDoNotContainValuesFromTheFile(String title, String yaml) {
        assertThatThrownBy(() -> YamlProjectProfileSource.parse(yaml))
                .isInstanceOf(InvalidProfileException.class)
                .satisfies(e -> {
                    assertThat(e.getMessage()).doesNotContain("SECRET123");
                    assertThat(e.getCause()).isNull();
                });
    }

    static Stream<Arguments> profilesWithSecrets() {
        return Stream.of(
                Arguments.of("синтаксическая ошибка", MINIMAL + "owners: [SECRET123\n"),
                Arguments.of("повторяющийся ключ", MINIMAL + "owners: [a]\nowners: [SECRET123]\n"),
                Arguments.of("неверный тип", MINIMAL.replace("restricted: false", "restricted: SECRET123")),
                Arguments.of("плохое выражение", MINIMAL + "data_policy:\n  mask: ['(SECRET123']\n"),
                Arguments.of("пароль в ссылке", MINIMAL.replace(REPO, "https://user:SECRET123@ahcode.example.invalid/g.git")),
                Arguments.of("плохая длительность", withTimeout(MINIMAL, "SECRET123")),
                Arguments.of("плохое имя проекта", MINIMAL.replace("project: demo", "project: SECRET123 !")));
    }

    @Test
    void unknownKeyInMessageIsShortenedAndSanitized() {
        String yaml = MINIMAL + "\"" + "k".repeat(100) + "\\nX\": 1\n";

        assertThatThrownBy(() -> YamlProjectProfileSource.parse(yaml))
                .isInstanceOf(InvalidProfileException.class)
                .satisfies(e -> {
                    assertThat(e.getMessage()).hasSizeLessThan(80).contains("…").doesNotContain("\n");
                });
    }

    @Test
    void profileIsLoadedFromFileInUtf8(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("at-profile.yaml");
        Files.writeString(file, MINIMAL + "owners: [команда-тестирования]\n", StandardCharsets.UTF_8);

        assertThat(new YamlProjectProfileSource(file).load().owners()).containsExactly("команда-тестирования");
    }

    @Test
    void missingFileIsReportedWithItsPath(@TempDir Path dir) {
        Path file = dir.resolve("absent.yaml");

        assertThatThrownBy(() -> new YamlProjectProfileSource(file).load())
                .isInstanceOf(InvalidProfileException.class)
                .hasMessageContaining("не прочитан").hasMessageContaining("absent.yaml")
                .hasCauseInstanceOf(NoSuchFileException.class);
    }

    @Test
    void fileUpToTheSizeLimitIsReadAndLargerIsRejected(@TempDir Path dir) throws IOException {
        int limit = YamlProjectProfileSource.MAX_SIZE_BYTES;
        Path atLimit = dir.resolve("at-limit.yaml");
        Path overLimit = dir.resolve("over-limit.yaml");
        Files.writeString(atLimit, MINIMAL + "#".repeat(limit - MINIMAL.length()));
        Files.writeString(overLimit, MINIMAL + "#".repeat(limit - MINIMAL.length() + 1));

        assertThat(new YamlProjectProfileSource(atLimit).load().project()).isEqualTo("demo");
        assertThatThrownBy(() -> new YamlProjectProfileSource(overLimit).load())
                .isInstanceOf(InvalidProfileException.class).hasMessageContaining("больше 64 КБ");
    }

    @Test
    void fileNotInUtf8IsRejected(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("windows-1251.yaml");
        Files.write(file, new byte[]{'o', 'w', 'n', 'e', 'r', 's', ':', ' ', (byte) 0xC3, (byte) 0x28});

        assertThatThrownBy(() -> new YamlProjectProfileSource(file).load())
                .isInstanceOf(InvalidProfileException.class).hasMessageContaining("UTF-8");
    }

    private static Arguments bad(String title, String yaml, String messageStart) {
        return Arguments.of(title, yaml, messageStart);
    }

    private static String withTimeout(String yaml, String timeout) {
        return yaml.replace("  command: [gradle, test]\n", "  command: [gradle, test]\n  timeout: " + timeout + "\n");
    }

    private static String neverSend(String item) {
        return MINIMAL + "data_policy:\n  never_send: [" + item + "]\n";
    }

    private static String items(int count) {
        return IntStream.range(0, count).mapToObj(i -> "a" + i).collect(Collectors.joining(", ", "[", "]"));
    }
}
