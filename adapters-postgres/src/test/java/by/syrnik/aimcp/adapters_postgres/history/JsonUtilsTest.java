package by.syrnik.aimcp.adapters_postgres.history;

import by.syrnik.aimcp.core.history.enums.FailureKind;
import by.syrnik.aimcp.core.history.enums.TestStatus;
import by.syrnik.aimcp.core.history.records.Failure;
import by.syrnik.aimcp.core.history.records.Link;
import by.syrnik.aimcp.core.history.records.Step;
import by.syrnik.aimcp.core.history.records.Suppressed;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Формат JSON в колонках jsonb: время шагов в миллисекундах, вложения как {@code [{"path": ...}]}.
 * Формат описан в комментариях колонок V001 и в контракте файла результатов.
 */
class JsonUtilsTest {

    private static final Instant T0 = Instant.parse("2026-03-01T10:00:00Z");

    @Test
    void stepsKeepAllFieldsAndNestingOnRoundTrip() {
        Failure failure = new Failure(FailureKind.ASSERTION, "java.lang.AssertionError", "не совпало", "at Foo.bar",
                List.of(new Suppressed("java.io.IOException", "причина")));
        Step deepest = new Step("глубокий", null, TestStatus.SKIPPED, null, null, Map.of(), List.of(), null, List.of());
        Step inner = new Step("вложенный", "описание", TestStatus.PASSED, T0, T0.plusMillis(5),
                Map.of(), List.of("/tmp/inner.png"), null, List.of(deepest));
        Step outer = new Step("внешний", null, TestStatus.FAILED, T0.plusMillis(10), T0.plusMillis(20),
                Map.of("url", "http://stand"), List.of("/tmp/a.png", "/tmp/b.png"), failure, List.of(inner));

        assertThat(JsonUtils.stepsFromJson(JsonUtils.stepsToJson(List.of(outer)))).containsExactly(outer);
    }

    @Test
    void stepTimesAreStoredAsEpochMilliseconds() throws Exception {
        Step step = new Step("шаг", null, TestStatus.PASSED, T0.plusMillis(10), T0.plusMillis(20),
                Map.of(), List.of(), null, List.of());

        JsonNode stored = JsonUtils.MAPPER.readTree(JsonUtils.stepsToJson(List.of(step))).get(0);

        assertThat(stored.get("startMs").asLong()).isEqualTo(T0.plusMillis(10).toEpochMilli());
        assertThat(stored.get("stopMs").asLong()).isEqualTo(T0.plusMillis(20).toEpochMilli());
    }

    @Test
    void stepWithoutTimesKeepsThemAbsent() throws Exception {
        Step step = new Step("шаг", null, TestStatus.PASSED, null, null, Map.of(), List.of(), null, List.of());

        JsonNode stored = JsonUtils.MAPPER.readTree(JsonUtils.stepsToJson(List.of(step))).get(0);

        assertThat(stored.get("startMs").isNull()).isTrue();
        assertThat(JsonUtils.stepsFromJson(JsonUtils.stepsToJson(List.of(step)))).containsExactly(step);
    }

    @Test
    void stepParameterWithNullValueSurvives() {
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("есть", "значение");
        parameters.put("нет", null);
        Step step = new Step("шаг", null, TestStatus.PASSED, null, null, parameters, List.of(), null, List.of());

        assertThat(JsonUtils.stepsFromJson(JsonUtils.stepsToJson(List.of(step)))).containsExactly(step);
    }

    @Test
    void emptyListsAreStoredAsEmptyArrays() {
        assertThat(JsonUtils.stepsToJson(List.of())).isEqualTo("[]");
        assertThat(JsonUtils.attachmentsToJson(List.of())).isEqualTo("[]");
        assertThat(JsonUtils.stepsFromJson("[]")).isEmpty();
        assertThat(JsonUtils.attachmentsFromJson("[]")).isEmpty();
    }

    @Test
    void attachmentsAreStoredAsObjectsWithPathAndKeepOrder() {
        String json = JsonUtils.attachmentsToJson(List.of("/tmp/b.png", "/tmp/a.png"));

        assertThat(json).isEqualTo("[{\"path\":\"/tmp/b.png\"},{\"path\":\"/tmp/a.png\"}]");
        assertThat(JsonUtils.attachmentsFromJson(json)).containsExactly("/tmp/b.png", "/tmp/a.png");
    }

    @Test
    void recordsAreWrittenAndReadBack() {
        List<Link> links = List.of(new Link("Issue", "баг", "http://tracker/1"), new Link(null, null, "http://tracker/2"));

        String json = JsonUtils.toJson(links, "[]");

        assertThat(JsonUtils.fromJson(json, new TypeReference<List<Link>>() {
        })).isEqualTo(links);
    }

    @Test
    void nullValueIsWrittenAsFallback() {
        assertThat(JsonUtils.toJson(null, "[]")).isEqualTo("[]");
        assertThat(JsonUtils.toJson(null, "{}")).isEqualTo("{}");
    }

    @Test
    void brokenJsonIsReportedWithAReadableMessage() {
        assertThatThrownBy(() -> JsonUtils.stepsFromJson("{"))
                .isInstanceOf(RuntimeException.class).hasMessageContaining("Ошибка десериализации");
        assertThatThrownBy(() -> JsonUtils.attachmentsFromJson("{"))
                .isInstanceOf(RuntimeException.class).hasMessageContaining("Ошибка десериализации");
        assertThatThrownBy(() -> JsonUtils.fromJson("{", new TypeReference<Map<String, String>>() {
        })).isInstanceOf(RuntimeException.class).hasMessageContaining("Ошибка десериализации");
    }
}
