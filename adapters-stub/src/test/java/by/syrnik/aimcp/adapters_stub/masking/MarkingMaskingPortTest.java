package by.syrnik.aimcp.adapters_stub.masking;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MarkingMaskingPortTest {

    @Test
    void textIsWrappedSoTestsSeeItPassedThroughThePort() {
        assertThat(new MarkingMaskingPort().mask("log line")).isEqualTo("MASKED(log line)");
    }

    @Test
    void nullTextIsRejected() {
        assertThatThrownBy(() -> new MarkingMaskingPort().mask(null)).isInstanceOf(NullPointerException.class);
    }
}
