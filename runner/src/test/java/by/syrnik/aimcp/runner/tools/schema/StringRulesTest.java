package by.syrnik.aimcp.runner.tools.schema;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StringRulesTest {

    @Test
    void appliesDefaultMaxLength() {
        assertThat(new StringRules(null, null, null, List.of()).maxLength())
                .isEqualTo(StringRules.DEFAULT_MAX_LENGTH)
                .isEqualTo(256);
        assertThat(StringRules.NONE.maxLength()).isEqualTo(256);
    }

    @Test
    void keepsExplicitValues() {
        StringRules r = new StringRules(1, 64, "[a-z]+", List.of("dev", "test"));

        assertThat(r.minLength()).isEqualTo(1);
        assertThat(r.maxLength()).isEqualTo(64);
        assertThat(r.pattern()).isEqualTo("[a-z]+");
        assertThat(r.enumValues()).containsExactly("dev", "test");
    }

    @Test
    void rejectsNonPositiveMaxLength() {
        assertThatThrownBy(() -> new StringRules(null, 0, null, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StringRules(null, -1, null, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void checksMinLengthBounds() {
        assertThat(new StringRules(0, 10, null, List.of()).minLength()).isZero();
        assertThat(new StringRules(10, 10, null, List.of()).minLength()).isEqualTo(10);

        assertThatThrownBy(() -> new StringRules(-1, 10, null, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StringRules(11, 10, null, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StringRules(257, null, null, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsInvalidPattern() {
        assertThatThrownBy(() -> new StringRules(null, null, "[a-z", List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enumIsRequiredAndCopied() {
        assertThatThrownBy(() -> new StringRules(null, null, null, null))
                .isInstanceOf(NullPointerException.class);

        List<String> source = new ArrayList<>(List.of("a"));
        StringRules r = new StringRules(null, null, null, source);
        source.add("b");

        assertThat(r.enumValues()).containsExactly("a");
        assertThatThrownBy(() -> r.enumValues().add("c"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsBadEnumValues() {
        assertThatThrownBy(() -> new StringRules(null, null, null, List.of("a", " ")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StringRules(null, null, null, List.of("a", "a")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StringRules(null, 3, null, List.of("abcd")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
