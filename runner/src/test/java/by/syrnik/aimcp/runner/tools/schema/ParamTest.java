package by.syrnik.aimcp.runner.tools.schema;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ParamTest {

    @ParameterizedTest
    @ValueSource(strings = {"a", "stand", "run_id", "x1", "a_b_c_123"})
    void acceptsValidNames(String name) {
        assertThat(new BooleanParam(name, "d", false).name()).isEqualTo(name);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "Stand", "1abc", "_abc", "run-id", "run id", "имя"})
    void rejectsInvalidNames(String name) {
        assertThatThrownBy(() -> new BooleanParam(name, "d", false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nameIsAtMost64Chars() {
        String max = "a" + "b".repeat(63);
        assertThat(new BooleanParam(max, "d", false).name()).hasSize(64);
        assertThatThrownBy(() -> new BooleanParam(max + "c", "d", false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nameAndDescriptionAreRequired() {
        assertThatThrownBy(() -> new BooleanParam(null, "d", false))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new BooleanParam("a", null, false))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new BooleanParam("a", "  ", false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void everyParamTypeValidatesNameAndDescription() {
        assertThatThrownBy(() -> new StringParam("Bad", "d", false, StringRules.NONE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new IntegerParam("Bad", "d", false, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StringArrayParam("Bad", "d", false, null, StringRules.NONE))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new StringParam("a", "", false, StringRules.NONE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new IntegerParam("a", "", false, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StringArrayParam("a", "", false, null, StringRules.NONE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void stringParamRequiresRules() {
        assertThatThrownBy(() -> new StringParam("a", "d", true, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void integerParamChecksRange() {
        assertThat(new IntegerParam("a", "d", false, null, null).minimum()).isNull();
        assertThat(new IntegerParam("a", "d", false, 5L, null).minimum()).isEqualTo(5L);
        assertThat(new IntegerParam("a", "d", false, null, 5L).maximum()).isEqualTo(5L);
        assertThat(new IntegerParam("a", "d", false, 5L, 5L).maximum()).isEqualTo(5L);

        assertThatThrownBy(() -> new IntegerParam("a", "d", false, 6L, 5L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void stringArrayParamAppliesDefaultMaxItems() {
        StringArrayParam p = new StringArrayParam("tags", "d", false, null, StringRules.NONE);

        assertThat(p.maxItems())
                .isEqualTo(StringArrayParam.DEFAULT_MAX_ITEMS)
                .isEqualTo(100);
        assertThat(new StringArrayParam("tags", "d", false, 1, StringRules.NONE).maxItems()).isEqualTo(1);
    }

    @Test
    void stringArrayParamValidatesMaxItemsAndItems() {
        assertThatThrownBy(() -> new StringArrayParam("tags", "d", false, 0, StringRules.NONE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StringArrayParam("tags", "d", false, -1, StringRules.NONE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StringArrayParam("tags", "d", false, 10, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void keepsRequiredFlag() {
        List<Param> params = List.of(
                new StringParam("s", "d", true, StringRules.NONE),
                new IntegerParam("i", "d", true, null, null),
                new BooleanParam("b", "d", true),
                new StringArrayParam("a", "d", true, null, StringRules.NONE));

        assertThat(params).allMatch(Param::required);
    }
}
