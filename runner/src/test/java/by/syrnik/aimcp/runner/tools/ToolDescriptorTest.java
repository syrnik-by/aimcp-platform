package by.syrnik.aimcp.runner.tools;

import by.syrnik.aimcp.runner.tools.schema.BooleanParam;
import by.syrnik.aimcp.runner.tools.schema.Param;
import by.syrnik.aimcp.runner.tools.schema.StringParam;
import by.syrnik.aimcp.runner.tools.schema.StringRules;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolDescriptorTest {

    @Test
    void keepsFields() {
        BooleanParam p = new BooleanParam("verbose", "d", false);

        ToolDescriptor t = new ToolDescriptor("check_env", "Проверка", List.of(p), true, false);

        assertThat(t.name()).isEqualTo("check_env");
        assertThat(t.description()).isEqualTo("Проверка");
        assertThat(t.params()).containsExactly(p);
        assertThat(t.readOnly()).isTrue();
        assertThat(t.idempotent()).isFalse();
    }

    @Test
    void allowsNoParams() {
        assertThat(new ToolDescriptor("ping", "d", List.of(), true, true).params()).isEmpty();
    }

    @Test
    void validatesNameAndDescription() {
        assertThatThrownBy(() -> new ToolDescriptor("Check-Env", "d", List.of(), true, true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ToolDescriptor(null, "d", List.of(), true, true))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ToolDescriptor("ping", " ", List.of(), true, true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void paramsAreRequiredAndCopied() {
        assertThatThrownBy(() -> new ToolDescriptor("ping", "d", null, true, true))
                .isInstanceOf(NullPointerException.class);

        List<Param> source = new ArrayList<>(List.of(new BooleanParam("a", "d", false)));
        ToolDescriptor t = new ToolDescriptor("ping", "d", source, true, true);
        source.add(new BooleanParam("b", "d", false));

        assertThat(t.params()).hasSize(1);
        assertThatThrownBy(() -> t.params().add(new BooleanParam("c", "d", false)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsDuplicateParamNames() {
        List<Param> params = List.of(
                new BooleanParam("stand", "d", false),
                new StringParam("stand", "d", true, StringRules.NONE));

        assertThatThrownBy(() -> new ToolDescriptor("check_env", "d", params, true, true))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
