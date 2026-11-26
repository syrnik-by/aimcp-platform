package by.syrnik.aimcp.runner.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RunnerSettingsTest {

    private static final String TOKEN = "t".repeat(32);
    private static final String PROFILE = "at-profile.yaml";
    private static final Duration BUDGET = Duration.ofSeconds(5);

    @Test
    void validValuesAreKept() {
        RunnerSettings s = new RunnerSettings(TOKEN, PROFILE, 65536, 1048576, BUDGET);

        assertThat(s.getToken()).isEqualTo(TOKEN);
        assertThat(s.getProfile()).isEqualTo(PROFILE);
        assertThat(s.getMaxRequestBytes()).isEqualTo(65536);
        assertThat(s.getMaxResponseBytes()).isEqualTo(1048576);
        assertThat(s.getMaskingBudget()).isEqualTo(BUDGET);
    }

    @Test
    void tokenOfExactly32CharsIsAccepted() {
        assertThat(new RunnerSettings("x".repeat(32), PROFILE, 1, 1, BUDGET).getToken()).hasSize(32);
    }

    @Test
    void tokenOf31CharsIsRejected() {
        assertThatThrownBy(() -> new RunnerSettings("x".repeat(31), PROFILE, 1, 1, BUDGET))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void missingTokenIsRejected() {
        assertThatThrownBy(() -> new RunnerSettings(null, PROFILE, 1, 1, BUDGET))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void emptyTokenIsRejected() {
        assertThatThrownBy(() -> new RunnerSettings("", PROFILE, 1, 1, BUDGET))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectionDoesNotRevealTheToken() {
        String shortSecret = "SECRET-31-chars-xxxxxxxxxxxxxxx";

        assertThatThrownBy(() -> new RunnerSettings(shortSecret, PROFILE, 1, 1, BUDGET))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("AIMCP_RUNNER_TOKEN")
                .hasMessageNotContaining(shortSecret);
    }

    @Test
    void toStringDoesNotRevealTheToken() {
        assertThat(new RunnerSettings(TOKEN, PROFILE, 1, 1, BUDGET).toString()).doesNotContain(TOKEN);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, Integer.MIN_VALUE})
    void nonPositiveRequestLimitIsRejected(int limit) {
        assertThatThrownBy(() -> new RunnerSettings(TOKEN, PROFILE, limit, 1, BUDGET))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, Integer.MIN_VALUE})
    void nonPositiveResponseLimitIsRejected(int limit) {
        assertThatThrownBy(() -> new RunnerSettings(TOKEN, PROFILE, 1, limit, BUDGET))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void limitsOfOneByteAreAccepted() {
        RunnerSettings s = new RunnerSettings(TOKEN, PROFILE, 1, 1, BUDGET);

        assertThat(s.getMaxRequestBytes()).isEqualTo(1);
        assertThat(s.getMaxResponseBytes()).isEqualTo(1);
    }

    @Test
    void wholeSecondBudgetIsAccepted() {
        assertThat(new RunnerSettings(TOKEN, PROFILE, 1, 1, Duration.ofSeconds(5)).getMaskingBudget())
                .isEqualTo(Duration.ofSeconds(5));
    }

    @Test
    void subSecondBudgetIsAccepted() {
        assertThat(new RunnerSettings(TOKEN, PROFILE, 1, 1, Duration.ofMillis(1)).getMaskingBudget())
                .isEqualTo(Duration.ofMillis(1));
    }

    @Test
    void zeroBudgetIsRejected() {
        assertThatThrownBy(() -> new RunnerSettings(TOKEN, PROFILE, 1, 1, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void negativeBudgetIsRejected() {
        assertThatThrownBy(() -> new RunnerSettings(TOKEN, PROFILE, 1, 1, Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void missingBudgetIsRejectedWithClearReason() {
        assertThatThrownBy(() -> new RunnerSettings(TOKEN, PROFILE, 1, 1, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
