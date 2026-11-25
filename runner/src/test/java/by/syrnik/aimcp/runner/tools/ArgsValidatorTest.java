package by.syrnik.aimcp.runner.tools;

import by.syrnik.aimcp.runner.tools.schema.BooleanParam;
import by.syrnik.aimcp.runner.tools.schema.IntegerParam;
import by.syrnik.aimcp.runner.tools.schema.Param;
import by.syrnik.aimcp.runner.tools.schema.StringArrayParam;
import by.syrnik.aimcp.runner.tools.schema.StringParam;
import by.syrnik.aimcp.runner.tools.schema.StringRules;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArgsValidatorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final ToolDescriptor TOOL = tool(
            new StringParam("host", "d", true, new StringRules(1, 10, "^[a-z]+$", List.of())),
            new StringParam("mode", "d", false, new StringRules(null, null, null, List.of("fast", "slow"))),
            new IntegerParam("port", "d", false, 1L, 65535L),
            new BooleanParam("verbose", "d", false),
            new StringArrayParam("tags", "d", false, 2, new StringRules(null, 3, null, List.of())));

    private static ToolDescriptor tool(Param... params) {
        return new ToolDescriptor("check_env", "d", List.of(params), true, true);
    }

    private static JsonNode json(String s) {
        try {
            return MAPPER.readTree(s.replace('\'', '"'));
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    private static Map<String, Object> validate(String args) {
        return ArgsValidator.validate(TOOL, json(args));
    }

    private static void assertInvalid(ThrowingCallable call, String message) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(ToolException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_ARGUMENTS))
                .hasMessage(message);
    }

    private static void assertInvalid(String args, String message) {
        assertInvalid(() -> validate(args), message);
    }

    @Test
    void convertsAllTypesInDescriptorOrder() {
        Map<String, Object> r = validate(
                "{'tags':['a','bc'],'verbose':true,'port':8080,'mode':'fast','host':'db'}");

        assertThat(r).containsExactly(
                Map.entry("host", "db"),
                Map.entry("mode", "fast"),
                Map.entry("port", 8080L),
                Map.entry("verbose", true),
                Map.entry("tags", List.of("a", "bc")));
    }

    @Test
    void omitsAbsentOptionalParams() {
        assertThat(validate("{'host':'db'}")).containsOnlyKeys("host");
    }

    @Test
    void emptyObjectIsOkWhenNoParams() {
        assertThat(ArgsValidator.validate(tool(), json("{}"))).isEmpty();
    }

    @Test
    void resultIsUnmodifiable() {
        Map<String, Object> r = validate("{'host':'db','tags':['a']}");

        assertThatThrownBy(() -> r.put("x", 1)).isInstanceOf(UnsupportedOperationException.class);
        @SuppressWarnings("unchecked")
        List<String> tags = (List<String>) r.get("tags");
        assertThatThrownBy(() -> tags.add("b")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsNullArgs() {
        assertInvalid(() -> ArgsValidator.validate(TOOL, null), "аргументы должны быть объектом");
    }

    @ParameterizedTest
    @ValueSource(strings = {"[]", "'x'", "1", "null", "true"})
    void rejectsNonObjectArgs(String args) {
        assertInvalid(args, "аргументы должны быть объектом");
    }

    @Test
    void rejectsUnknownArgument() {
        assertInvalid("{'host':'db','extra':1}", "extra: неизвестный аргумент");
    }

    @Test
    void unknownArgumentIsReportedBeforeMissingRequired() {
        assertInvalid("{'extra':1}", "extra: неизвестный аргумент");
    }

    @Test
    void rejectsMissingRequired() {
        assertInvalid("{}", "host: обязательный");
    }

    @Test
    void explicitNullIsTypeError() {
        assertInvalid("{'host':null}", "host: ожидается string");
        assertInvalid("{'host':'db','port':null}", "port: ожидается integer");
    }

    @Test
    void rejectsWrongStringType() {
        assertInvalid("{'host':5}", "host: ожидается string");
    }

    @Test
    void checksStringLengthBounds() {
        assertInvalid("{'host':''}", "host: длина меньше 1");
        assertThat(validate("{'host':'abcdefghij'}")).containsEntry("host", "abcdefghij");
        assertInvalid("{'host':'abcdefghijk'}", "host: длина больше 10");
    }

    @Test
    void stringLengthCountsCodePoints() {
        ToolDescriptor t = tool(new StringParam("s", "d", true, new StringRules(null, 2, null, List.of())));

        assertThat(ArgsValidator.validate(t, json("{'s':'\uD83D\uDE00\uD83D\uDE00'}"))).containsKey("s");
        assertInvalid(() -> ArgsValidator.validate(t, json("{'s':'\uD83D\uDE00\uD83D\uDE00a'}")),
                "s: длина больше 2");
    }

    @Test
    void appliesDefaultMaxLength() {
        ToolDescriptor t = tool(new StringParam("s", "d", true, StringRules.NONE));
        String ok = "a".repeat(StringRules.DEFAULT_MAX_LENGTH);

        assertThat(ArgsValidator.validate(t, json("{'s':'" + ok + "'}"))).containsEntry("s", ok);
        assertInvalid(() -> ArgsValidator.validate(t, json("{'s':'" + ok + "a'}")),
                "s: длина больше " + StringRules.DEFAULT_MAX_LENGTH);
    }

    @Test
    void checksPattern() {
        assertInvalid("{'host':'DB'}", "host: не подходит под pattern ^[a-z]+$");
    }

    @Test
    void patternIsNotImplicitlyAnchored() {
        ToolDescriptor t = tool(new StringParam("s", "d", true, new StringRules(null, null, "[0-9]", List.of())));

        assertThat(ArgsValidator.validate(t, json("{'s':'abc1def'}"))).containsEntry("s", "abc1def");
    }

    @Test
    void checksEnum() {
        assertInvalid("{'host':'db','mode':'FAST'}", "mode: не из списка [fast, slow]");
    }

    @Test
    void checksIntegerBounds() {
        assertThat(validate("{'host':'db','port':1}")).containsEntry("port", 1L);
        assertThat(validate("{'host':'db','port':65535}")).containsEntry("port", 65535L);
        assertInvalid("{'host':'db','port':0}", "port: меньше 1");
        assertInvalid("{'host':'db','port':65536}", "port: больше 65535");
    }

    @ParameterizedTest
    @ValueSource(strings = {"'80'", "80.0", "80.5", "1e2", "true", "99999999999999999999"})
    void rejectsNonIntegerValues(String value) {
        assertInvalid("{'host':'db','port':" + value + "}", "port: ожидается integer");
    }

    @Test
    void integerWithoutBoundsAcceptsLongRange() {
        ToolDescriptor t = tool(new IntegerParam("n", "d", true, null, null));

        assertThat(ArgsValidator.validate(t, json("{'n':" + Long.MIN_VALUE + "}")))
                .containsEntry("n", Long.MIN_VALUE);
        assertThat(ArgsValidator.validate(t, json("{'n':" + Long.MAX_VALUE + "}")))
                .containsEntry("n", Long.MAX_VALUE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"'true'", "1", "0", "null"})
    void rejectsNonBooleanValues(String value) {
        assertInvalid("{'host':'db','verbose':" + value + "}", "verbose: ожидается boolean");
    }

    @Test
    void rejectsNonArrayForStringArray() {
        assertInvalid("{'host':'db','tags':'a'}", "tags: ожидается array");
    }

    @Test
    void acceptsEmptyArray() {
        assertThat(validate("{'host':'db','tags':[]}")).containsEntry("tags", List.of());
    }

    @Test
    void checksMaxItems() {
        assertInvalid("{'host':'db','tags':['a','b','c']}", "tags: элементов больше 2");
    }

    @Test
    void reportsArrayItemPath() {
        assertInvalid("{'host':'db','tags':['a',1]}", "tags[1]: ожидается string");
        assertInvalid("{'host':'db','tags':['a','abcd']}", "tags[1]: длина больше 3");
    }
}
