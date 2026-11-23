package by.syrnik.aimcp.techzone.ingest;

import by.syrnik.aimcp.core.history.enums.FailureKind;
import by.syrnik.aimcp.core.history.enums.TestStatus;
import by.syrnik.aimcp.core.history.records.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;

import java.time.Instant;
import java.util.*;

public class ResultFileParser {

    static final int SUPPORTED_SCHEMA_VERSION = 1;

    /**
     * Разбирает один документ контракта результатов (docs/results-file-format.md).
     * Пустой результат: версия формата неизвестна, документ надо пропустить.
     * InvalidResultFileException: документ повреждён, в сообщении путь к полю (JSON Pointer) и что с ним не так.
     */
    public Optional<ParsedResult> parse(JsonNode document) {
        if (document == null || !document.isObject()) {
            throw new InvalidResultFileException("/: документ должен быть JSON-объектом");
        }

        JsonNode version = document.path("schemaVersion");
        if (!version.isIntegralNumber()) {
            throw new InvalidResultFileException("/schemaVersion: обязательное целое число");
        }
        if (!version.canConvertToInt() || version.intValue() != SUPPORTED_SCHEMA_VERSION) {
            return Optional.empty();
        }

        JsonNode run = requiredObject(document.path("run"), "/run");
        JsonNode test = requiredObject(document.path("test"), "/test");

        return Optional.of(new ParsedResult(
                requiredText(run.path("runId"), "/run/runId"),
                parseRunInfo(run),
                parseTestResult(version.intValue(), run, test)));
    }

    private RunInfo parseRunInfo(JsonNode run) {
        JsonNode ci = optionalObject(run.path("ci"), "/run/ci");

        return new RunInfo(
                optionalText(ci.path("pipelineId"), "/run/ci/pipelineId"),
                optionalText(ci.path("commitSha"), "/run/ci/commitSha"),
                optionalText(ci.path("ref"), "/run/ci/ref"),
                optionalText(ci.path("project"), "/run/ci/project"),
                optionalText(ci.path("mergeRequestIid"), "/run/ci/mergeRequestIid")
        );
    }

    private TestResult parseTestResult(int schemaVersion, JsonNode run, JsonNode test) {
        JsonNode ci = optionalObject(run.path("ci"), "/run/ci");

        return new TestResult(
                new PartInfo(
                        optionalText(ci.path("jobId"), "/run/ci/jobId"),
                        optionalText(ci.path("jobUrl"), "/run/ci/jobUrl"),
                        optionalText(ci.path("jobName"), "/run/ci/jobName"),
                        requiredText(run.path("host"), "/run/host"),
                        requiredText(run.path("adapterVersion"), "/run/adapterVersion"),
                        optionalText(run.path("testItRunId"), "/run/testItRunId")
                ),
                schemaVersion,
                requiredText(test.path("uuid"), "/test/uuid"),
                requiredText(test.path("externalId"), "/test/externalId"),
                parseStringList(test.path("workItemIds"), "/test/workItemIds"),
                optionalText(test.path("namespace"), "/test/namespace"),
                optionalText(test.path("className"), "/test/className"),
                requiredText(test.path("name"), "/test/name"),
                optionalText(test.path("title"), "/test/title"),
                optionalText(test.path("description"), "/test/description"),
                optionalText(test.path("message"), "/test/message"),
                requiredEnum(test.path("status"), "/test/status", TestStatus.class),
                optionalMillis(test.path("startMs"), "/test/startMs"),
                optionalMillis(test.path("stopMs"), "/test/stopMs"),
                parseStringList(test.path("labels"), "/test/labels"),
                parseLinks(test.path("links"), "/test/links"),
                parseParameters(test.path("parameters"), "/test/parameters"),
                parseSteps(test.path("steps"), "/test/steps"),
                parseAttachments(test.path("attachments"), "/test/attachments"),
                parseFailure(test.path("failure"), "/test/failure")
        );
    }

    private static List<Step> parseSteps(JsonNode node, String where) {
        JsonNode array = optionalArray(node, where);
        List<Step> steps = new ArrayList<>(array.size());
        for (int i = 0; i < array.size(); i++) {
            steps.add(parseStep(array.get(i), where + "/" + i));
        }
        return steps;
    }

    private static Step parseStep(JsonNode node, String where) {
        JsonNode step = requiredObject(node, where);

        return new Step(
                requiredText(step.path("name"), where + "/name"),
                optionalText(step.path("description"), where + "/description"),
                requiredEnum(step.path("status"), where + "/status", TestStatus.class),
                optionalMillis(step.path("startMs"), where + "/startMs"),
                optionalMillis(step.path("stopMs"), where + "/stopMs"),
                parseParameters(step.path("parameters"), where + "/parameters"),
                parseAttachments(step.path("attachments"), where + "/attachments"),
                parseFailure(step.path("failure"), where + "/failure"),
                parseSteps(step.path("steps"), where + "/steps")
        );
    }

    private static Failure parseFailure(JsonNode node, String where) {
        if (isAbsent(node)) {
            return null;
        }
        JsonNode failure = requiredObject(node, where);

        return new Failure(
                requiredEnum(failure.path("kind"), where + "/kind", FailureKind.class),
                requiredText(failure.path("type"), where + "/type"),
                optionalText(failure.path("message"), where + "/message"),
                Objects.requireNonNullElse(optionalText(failure.path("trace"), where + "/trace"), ""),
                parseSuppressed(failure.path("suppressed"), where + "/suppressed")
        );
    }

    private static List<Suppressed> parseSuppressed(JsonNode node, String where) {
        JsonNode array = optionalArray(node, where);
        List<Suppressed> suppressed = new ArrayList<>(array.size());
        for (int i = 0; i < array.size(); i++) {
            String at = where + "/" + i;
            JsonNode item = requiredObject(array.get(i), at);
            suppressed.add(new Suppressed(
                    requiredText(item.path("type"), at + "/type"),
                    optionalText(item.path("message"), at + "/message")));
        }
        return suppressed;
    }

    private static List<Link> parseLinks(JsonNode node, String where) {
        JsonNode array = optionalArray(node, where);
        List<Link> links = new ArrayList<>(array.size());
        for (int i = 0; i < array.size(); i++) {
            String at = where + "/" + i;
            JsonNode item = requiredObject(array.get(i), at);
            links.add(new Link(
                    optionalText(item.path("type"), at + "/type"),
                    optionalText(item.path("title"), at + "/title"),
                    requiredText(item.path("url"), at + "/url")));
        }
        return links;
    }

    private static List<String> parseAttachments(JsonNode node, String where) {
        JsonNode array = optionalArray(node, where);
        List<String> paths = new ArrayList<>(array.size());
        for (int i = 0; i < array.size(); i++) {
            String at = where + "/" + i;
            JsonNode item = requiredObject(array.get(i), at);
            paths.add(requiredText(item.path("path"), at + "/path"));
        }
        return paths;
    }

    private static Map<String, String> parseParameters(JsonNode node, String where) {
        JsonNode object = optionalObject(node, where);
        Map<String, String> parameters = new LinkedHashMap<>();
        object.fields().forEachRemaining(entry ->
                parameters.put(entry.getKey(), optionalText(entry.getValue(), where + "/" + entry.getKey())));
        return parameters;
    }

    private static List<String> parseStringList(JsonNode node, String where) {
        JsonNode array = optionalArray(node, where);
        List<String> result = new ArrayList<>(array.size());
        for (int i = 0; i < array.size(); i++) {
            JsonNode item = array.get(i);
            if (!item.isTextual()) {
                throw new InvalidResultFileException(where + "/" + i + ": должна быть строка");
            }
            result.add(item.asText());
        }
        return result;
    }

    private static boolean isAbsent(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull();
    }

    private static JsonNode requiredObject(JsonNode node, String where) {
        if (isAbsent(node) || !node.isObject()) {
            throw new InvalidResultFileException(where + ": обязательный объект отсутствует или не является объектом");
        }
        return node;
    }

    private static JsonNode optionalObject(JsonNode node, String where) {
        if (isAbsent(node)) {
            return MissingNode.getInstance();
        }
        if (!node.isObject()) {
            throw new InvalidResultFileException(where + ": должен быть объектом");
        }
        return node;
    }

    private static JsonNode optionalArray(JsonNode node, String where) {
        if (isAbsent(node)) {
            return MissingNode.getInstance();
        }
        if (!node.isArray()) {
            throw new InvalidResultFileException(where + ": должен быть массивом");
        }
        return node;
    }

    private static String optionalText(JsonNode node, String where) {
        if (isAbsent(node)) {
            return null;
        }
        if (!node.isTextual()) {
            throw new InvalidResultFileException(where + ": должна быть строка");
        }
        return node.asText();
    }

    private static String requiredText(JsonNode node, String where) {
        String value = optionalText(node, where);
        if (value == null || value.isBlank()) {
            throw new InvalidResultFileException(where + ": обязательное строковое поле отсутствует или пусто");
        }
        return value;
    }

    private static Instant optionalMillis(JsonNode node, String where) {
        if (isAbsent(node)) {
            return null;
        }
        if (!node.isIntegralNumber() || !node.canConvertToLong()) {
            throw new InvalidResultFileException(where + ": должно быть целое число миллисекунд");
        }
        return Instant.ofEpochMilli(node.longValue());
    }

    private static <E extends Enum<E>> E requiredEnum(JsonNode node, String where, Class<E> type) {
        String text = requiredText(node, where);
        try {
            return Enum.valueOf(type, text);
        } catch (IllegalArgumentException e) {
            throw new InvalidResultFileException(
                    where + ": неизвестное значение, допустимо: " + Arrays.toString(type.getEnumConstants()), e);
        }
    }
}
