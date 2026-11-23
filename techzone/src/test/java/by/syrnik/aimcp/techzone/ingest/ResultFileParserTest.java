package by.syrnik.aimcp.techzone.ingest;

import by.syrnik.aimcp.core.history.enums.FailureKind;
import by.syrnik.aimcp.core.history.enums.TestStatus;
import by.syrnik.aimcp.core.history.records.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResultFileParserTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ResultFileParser parser = new ResultFileParser();

    @Test
    void contractExampleIsParsedIntoDomainRecords() {
        ParsedResult parsed = parser.parse(ResultDocuments.example()).orElseThrow();

        assertThat(parsed.runId()).isEqualTo("310045-regression");
        assertThat(parsed.runInfo()).isEqualTo(new RunInfo("310045", "3f2a9c1d7e8b4a5f6c0d1e2f3a4b5c6d7e8f9a0b",
                "feature/payments", "group/project-tests", null));
        assertThat(parsed.testResult()).isEqualTo(expectedExampleResult());
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 2, 99, 4294967297L})
    void unknownSchemaVersionIsSkipped(long version) {
        ObjectNode document = ResultDocuments.example();
        document.put("schemaVersion", version);

        assertThat(parser.parse(document)).isEmpty();
    }

    @Test
    void unknownVersionIsSkippedBeforeTheRestOfTheDocumentIsChecked() {
        ObjectNode document = ResultDocuments.example();
        document.put("schemaVersion", 2);
        document.remove("run");

        assertThat(parser.parse(document)).isEmpty();
    }

    @Test
    void documentThatIsNotAJsonObjectIsRejected() {
        assertThatThrownBy(() -> parser.parse(null)).isInstanceOf(InvalidResultFileException.class);
        assertThatThrownBy(() -> parser.parse(json("[]"))).isInstanceOf(InvalidResultFileException.class);
        assertThatThrownBy(() -> parser.parse(json("\"text\""))).isInstanceOf(InvalidResultFileException.class);
        assertThatThrownBy(() -> parser.parse(json("null"))).isInstanceOf(InvalidResultFileException.class);
    }

    @Test
    void unknownFieldsAreIgnored() {
        ObjectNode document = ResultDocuments.example();
        for (String parent : List.of("", "/run", "/run/ci", "/test", "/test/links/0", "/test/failure",
                "/test/steps/0", "/test/steps/1/failure", "/test/steps/1/steps/0", "/test/steps/1/attachments/0")) {
            set(parent + "/addedInNextVersion", "{\"x\": 1}").accept(document);
        }

        assertThat(parser.parse(document).orElseThrow().testResult()).isEqualTo(expectedExampleResult());
    }

    @Test
    void absentOptionalFieldsGiveEmptyValues() {
        ObjectNode document = ResultDocuments.example();
        for (String pointer : List.of("/run/ci", "/test/workItemIds", "/test/labels", "/test/links",
                "/test/parameters", "/test/steps", "/test/attachments", "/test/failure")) {
            remove(pointer).accept(document);
        }

        ParsedResult parsed = parser.parse(document).orElseThrow();

        assertThat(parsed.runInfo()).isEqualTo(new RunInfo(null, null, null, null, null));
        TestResult result = parsed.testResult();
        assertThat(result.part()).isEqualTo(new PartInfo(null, null, null, "vm-example-01", "1.2.8", null));
        assertThat(result.workItemIds()).isEmpty();
        assertThat(result.labels()).isEmpty();
        assertThat(result.links()).isEmpty();
        assertThat(result.parameters()).isEmpty();
        assertThat(result.steps()).isEmpty();
        assertThat(result.attachments()).isEmpty();
        assertThat(result.failure()).isNull();
    }

    @Test
    void timesAreOptional() {
        ObjectNode document = ResultDocuments.example();
        set("/test/startMs", "null").accept(document);
        remove("/test/stopMs").accept(document);

        TestResult result = parser.parse(document).orElseThrow().testResult();

        assertThat(result.startedAt()).isNull();
        assertThat(result.stoppedAt()).isNull();
    }

    @Test
    void emptyTraceIsAllowed() {
        ObjectNode document = ResultDocuments.example();
        set("/test/failure/trace", "\"\"").accept(document);

        assertThat(parser.parse(document).orElseThrow().testResult().failure().trace()).isEmpty();
    }

    @Test
    void absentOrNullTraceBecomesEmpty() {
        ObjectNode withoutTrace = ResultDocuments.example();
        remove("/test/failure/trace").accept(withoutTrace);
        ObjectNode nullTrace = ResultDocuments.example();
        set("/test/failure/trace", "null").accept(nullTrace);

        assertThat(parser.parse(withoutTrace).orElseThrow().testResult().failure().trace()).isEmpty();
        assertThat(parser.parse(nullTrace).orElseThrow().testResult().failure().trace()).isEmpty();
    }

    @Test
    void suppressedErrorsAreParsed() {
        ObjectNode document = ResultDocuments.example();
        set("/test/failure/suppressed", "[{\"type\": \"java.io.IOException\", \"message\": null}]").accept(document);

        assertThat(parser.parse(document).orElseThrow().testResult().failure().suppressed())
                .containsExactly(new Suppressed("java.io.IOException", null));
    }

    @Test
    void unknownStatusAndKindNameTheAllowedValues() {
        ObjectNode badStatus = ResultDocuments.example();
        set("/test/steps/0/status", "\"WTF\"").accept(badStatus);
        ObjectNode badKind = ResultDocuments.example();
        set("/test/failure/kind", "\"WTF\"").accept(badKind);

        assertThatThrownBy(() -> parser.parse(badStatus))
                .isInstanceOf(InvalidResultFileException.class)
                .hasMessageStartingWith("/test/steps/0/status:")
                .hasMessageContaining(Arrays.toString(TestStatus.values()));
        assertThatThrownBy(() -> parser.parse(badKind))
                .isInstanceOf(InvalidResultFileException.class)
                .hasMessageStartingWith("/test/failure/kind:")
                .hasMessageContaining(Arrays.toString(FailureKind.values()));
    }

    @ParameterizedTest
    @MethodSource("corruptedDocuments")
    void corruptedDocumentIsRejectedWithThePathOfTheField(Corruption corruption) {
        ObjectNode document = ResultDocuments.example();
        corruption.apply().accept(document);

        assertThatThrownBy(() -> parser.parse(document))
                .isInstanceOf(InvalidResultFileException.class)
                .hasMessageStartingWith(corruption.pointer() + ":");
    }

    static Stream<Corruption> corruptedDocuments() {
        return Stream.of(
                new Corruption("нет schemaVersion", "/schemaVersion", remove("/schemaVersion")),
                new Corruption("schemaVersion строкой", "/schemaVersion", set("/schemaVersion", "\"1\"")),
                new Corruption("schemaVersion дробью", "/schemaVersion", set("/schemaVersion", "1.0")),
                new Corruption("нет run", "/run", remove("/run")),
                new Corruption("run массивом", "/run", set("/run", "[]")),
                new Corruption("нет test", "/test", remove("/test")),
                new Corruption("test массивом", "/test", set("/test", "[]")),
                new Corruption("нет runId", "/run/runId", remove("/run/runId")),
                new Corruption("runId null", "/run/runId", set("/run/runId", "null")),
                new Corruption("runId пробелы", "/run/runId", set("/run/runId", "\"  \"")),
                new Corruption("runId числом", "/run/runId", set("/run/runId", "5")),
                new Corruption("нет host", "/run/host", remove("/run/host")),
                new Corruption("нет adapterVersion", "/run/adapterVersion", remove("/run/adapterVersion")),
                new Corruption("ci массивом", "/run/ci", set("/run/ci", "[]")),
                new Corruption("ci.jobId числом", "/run/ci/jobId", set("/run/ci/jobId", "4821907")),
                new Corruption("нет uuid", "/test/uuid", remove("/test/uuid")),
                new Corruption("нет externalId", "/test/externalId", remove("/test/externalId")),
                new Corruption("нет name", "/test/name", remove("/test/name")),
                new Corruption("нет status", "/test/status", remove("/test/status")),
                new Corruption("неизвестный status", "/test/status", set("/test/status", "\"WTF\"")),
                new Corruption("startMs строкой", "/test/startMs", set("/test/startMs", "\"abc\"")),
                new Corruption("startMs дробью", "/test/startMs", set("/test/startMs", "1.5")),
                new Corruption("labels строкой", "/test/labels", set("/test/labels", "\"smoke\"")),
                new Corruption("label числом", "/test/labels/0", set("/test/labels", "[5]")),
                new Corruption("ссылка без url", "/test/links/0/url", remove("/test/links/0/url")),
                new Corruption("ссылка строкой", "/test/links/0", set("/test/links", "[\"http://x\"]")),
                new Corruption("параметр числом", "/test/parameters/stand", set("/test/parameters/stand", "5")),
                new Corruption("parameters массивом", "/test/parameters", set("/test/parameters", "[]")),
                new Corruption("failure без kind", "/test/failure/kind", remove("/test/failure/kind")),
                new Corruption("failure без type", "/test/failure/type", remove("/test/failure/type")),
                new Corruption("failure строкой", "/test/failure", set("/test/failure", "\"boom\"")),
                new Corruption("suppressed без type", "/test/failure/suppressed/0/type",
                        set("/test/failure/suppressed", "[{\"message\": \"m\"}]")),
                new Corruption("вложение без path", "/test/attachments/0/path", set("/test/attachments", "[{\"file\": \"a\"}]")),
                new Corruption("вложение строкой", "/test/attachments/0", set("/test/attachments", "[\"a\"]")),
                new Corruption("шаг без name", "/test/steps/0/name", remove("/test/steps/0/name")),
                new Corruption("шаг без status", "/test/steps/0/status", remove("/test/steps/0/status")),
                new Corruption("steps объектом", "/test/steps", set("/test/steps", "{}")),
                new Corruption("вложенный шаг: неизвестный status", "/test/steps/1/steps/0/status",
                        set("/test/steps/1/steps/0/status", "\"WTF\"")),
                new Corruption("вложенный шаг: stopMs строкой", "/test/steps/1/steps/0/stopMs",
                        set("/test/steps/1/steps/0/stopMs", "\"soon\"")),
                new Corruption("вложение шага без path", "/test/steps/1/attachments/0/path",
                        remove("/test/steps/1/attachments/0/path")),
                new Corruption("failure шага без type", "/test/steps/1/failure/type",
                        remove("/test/steps/1/failure/type"))
        );
    }

    record Corruption(String name, String pointer, Consumer<ObjectNode> apply) {
        @Override
        public String toString() {
            return name;
        }
    }

    private static Consumer<ObjectNode> remove(String pointer) {
        return document -> parent(document, pointer).remove(lastSegment(pointer));
    }

    private static Consumer<ObjectNode> set(String pointer, String jsonValue) {
        return document -> parent(document, pointer).set(lastSegment(pointer), json(jsonValue));
    }

    private static ObjectNode parent(ObjectNode document, String pointer) {
        return (ObjectNode) document.at(pointer.substring(0, pointer.lastIndexOf('/')));
    }

    private static String lastSegment(String pointer) {
        return pointer.substring(pointer.lastIndexOf('/') + 1);
    }

    private static JsonNode json(String text) {
        try {
            return MAPPER.readTree(text);
        } catch (Exception e) {
            throw new IllegalArgumentException(text, e);
        }
    }

    private static Instant ms(long millis) {
        return Instant.ofEpochMilli(millis);
    }

    private static TestResult expectedExampleResult() {
        Failure stepFailure = new Failure(FailureKind.ASSERTION, "org.opentest4j.AssertionFailedError",
                "expected: <200> but was: <500>",
                "org.opentest4j.AssertionFailedError: expected: <200> but was: <500>", List.of());
        Failure testFailure = new Failure(FailureKind.ASSERTION, "org.opentest4j.AssertionFailedError",
                "expected: <200> but was: <500>",
                "org.opentest4j.AssertionFailedError: expected: <200> but was: <500>\n"
                        + "\tat ru.example.payments.PaymentTest.paymentIsAccepted(PaymentTest.java:42)",
                List.of());

        Step openForm = new Step("Открыть форму платежа", null, TestStatus.PASSED,
                ms(1760000000100L), ms(1760000001200L), Map.of(), List.of(), null, List.of());
        Step checkCode = new Step("Проверить код ответа", null, TestStatus.FAILED,
                ms(1760000004000L), ms(1760000004100L), Map.of(), List.of(), null, List.of());
        Step sendPayment = new Step("Отправить платёж", "POST /payments", TestStatus.FAILED,
                ms(1760000001200L), ms(1760000004100L), Map.of("amount", "100"),
                List.of("build/attachments/response.txt"), stepFailure, List.of(checkCode));

        return new TestResult(
                new PartInfo("4821907", "https://ci.example.invalid/group/project-tests/-/jobs/4821907",
                        "regression 2/4", "vm-example-01", "1.2.8", null),
                1,
                "0b6f7a4e-8a52-4c1b-9d0e-5a3c2f1e7b90",
                "111111",
                List.of("1234"),
                "ru.example.payments",
                "PaymentTest",
                "paymentIsAccepted",
                "Платёж принимается",
                "Проверяется приём платежа на тестовом стенде",
                "",
                TestStatus.FAILED,
                ms(1760000000000L),
                ms(1760000004200L),
                List.of("smoke"),
                List.of(new Link("Defect", "BUG-1", "https://tracker.example.invalid/BUG-1")),
                Map.of("stand", "test"),
                List.of(openForm, sendPayment),
                List.of("build/attachments/screenshot.png"),
                testFailure);
    }
}
