package by.syrnik.aimcp.runner.tools;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolExceptionTest {

    @Test
    void keepsCodeAndMessage() {
        ToolException e = new ToolException(ErrorCode.NOT_FOUND, "прогон не найден");

        assertThat(e).isInstanceOf(RuntimeException.class);
        assertThat(e.code()).isEqualTo(ErrorCode.NOT_FOUND);
        assertThat(e.getMessage()).isEqualTo("прогон не найден");
        assertThat(e.getCause()).isNull();
    }

    @Test
    void keepsCause() {
        IllegalStateException cause = new IllegalStateException("причина");

        ToolException e = new ToolException(ErrorCode.TOOL_FAILED, "не вышло", cause);

        assertThat(e.code()).isEqualTo(ErrorCode.TOOL_FAILED);
        assertThat(e.getMessage()).isEqualTo("не вышло");
        assertThat(e.getCause()).isSameAs(cause);
    }

    @Test
    void codeIsRequired() {
        assertThatThrownBy(() -> new ToolException(null, "x")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ToolException(null, "x", new RuntimeException())).isInstanceOf(NullPointerException.class);
    }
}
