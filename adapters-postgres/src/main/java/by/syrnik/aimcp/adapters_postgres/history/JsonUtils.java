package by.syrnik.aimcp.adapters_postgres.history;

import by.syrnik.aimcp.core.history.enums.TestStatus;
import by.syrnik.aimcp.core.history.records.Failure;
import by.syrnik.aimcp.core.history.records.Step;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class JsonUtils {
    public static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonUtils() {
    }

    /**
     * Сериализует любой Record/Object в компактную JSON-строку.
     * Если передан null — возвращает fallbackJson (например, "[]" или "{}").
     */
    public static String toJson(Object value, String fallbackJson) {
        if (value == null) {
            return fallbackJson;
        }
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Ошибка сериализации в JSON: " + value.getClass().getName(), e);
        }
    }

    public static <T> T fromJson(String json, TypeReference<T> tTypeReference) {
        try {
            return MAPPER.readValue(json, tTypeReference);
        } catch (Exception e) {
            throw new RuntimeException("Ошибка десериализации JSON: ", e);
        }
    }

    public static String stepsToJson(List<Step> steps) {
        try {
            return MAPPER.writeValueAsString(stepsToNode(steps));
        } catch (Exception e) {
            throw new RuntimeException("Ошибка сериализации шагов в JSON: ", e);
        }
    }

    private static ArrayNode stepsToNode(List<Step> steps) {
        ArrayNode array = MAPPER.createArrayNode();
        for (Step s : steps) {
            ObjectNode n = array.addObject();
            n.put("name", s.name());
            n.put("description", s.description());
            n.put("status", s.status().name());
            n.put("startMs", s.startedAt() == null ? null : s.startedAt().toEpochMilli());
            n.put("stopMs", s.stoppedAt() == null ? null : s.stoppedAt().toEpochMilli());
            n.set("parameters", MAPPER.valueToTree(s.parameters()));
            n.set("attachments", attachmentsToNode(s.attachments()));
            n.set("failure", MAPPER.valueToTree(s.failure()));
            n.set("steps", stepsToNode(s.steps()));
        }
        return array;
    }

    public static String attachmentsToJson(List<String> attachments) {
        try {
            return MAPPER.writeValueAsString(attachmentsToNode(attachments));
        } catch (Exception e) {
            throw new RuntimeException("Ошибка сериализации вложений в JSON: ", e);
        }
    }

    private static ArrayNode attachmentsToNode(List<String> attachments) {
        ArrayNode array = MAPPER.createArrayNode();
        for (String s : attachments) {
            ObjectNode n = array.addObject();
            n.put("path", s);
        }
        return array;
    }

    public static List<Step> stepsFromJson(String steps) {
        try {
            return stepsFromNode(MAPPER.readTree(steps));
        } catch (Exception e) {
            throw new RuntimeException("Ошибка десериализации шагов из JSON: ", e);
        }
    }

    public static List<Step> stepsFromNode(JsonNode arrayNode) {
        if (arrayNode == null || !arrayNode.isArray()) {
            return Collections.emptyList();
        }

        List<Step> steps = new ArrayList<>();
        for (JsonNode n : arrayNode) {
            steps.add(stepFromNode(n));
        }
        return steps;
    }

    private static Step stepFromNode(JsonNode n) {
        String name = n.path("name").asText(null);
        String description = n.hasNonNull("description") ? n.get("description").asText() : null;

        TestStatus status = null;
        if (n.hasNonNull("status")) {
            status = TestStatus.valueOf(n.get("status").asText());
        }

        Instant startedAt = n.hasNonNull("startMs")
                ? Instant.ofEpochMilli(n.get("startMs").asLong())
                : null;

        Instant stoppedAt = n.hasNonNull("stopMs")
                ? Instant.ofEpochMilli(n.get("stopMs").asLong())
                : null;

        Map<String, String> parameters = Collections.emptyMap();
        JsonNode paramsNode = n.get("parameters");
        if (paramsNode != null && !paramsNode.isNull()) {
            parameters = MAPPER.convertValue(paramsNode, new TypeReference<>() {
            });
        }

        List<String> attachments = attachmentsFromNode(n.get("attachments"));

        Failure failure = failureFromNode(n.get("failure"));

        List<Step> subSteps = stepsFromNode(n.get("steps"));

        return new Step(
                name,
                description,
                status,
                startedAt,
                stoppedAt,
                parameters,
                attachments,
                failure,
                subSteps
        );
    }

    public static List<String> attachmentsFromJson(String attachments) {
        try {
            return MAPPER.readTree(attachments).findValuesAsText("path");
        } catch (Exception e) {
            throw new RuntimeException("Ошибка десериализации вложений из JSON: ", e);
        }
    }

    public static List<String> attachmentsFromNode(JsonNode arrayNode) {
        if (arrayNode == null || !arrayNode.isArray()) {
            return Collections.emptyList();
        }
        List<String> attachments = new ArrayList<>();
        for (JsonNode n : arrayNode) {
            attachments.add(n.get("path").asText());
        }
        return attachments;
    }

    public static Failure failureFromNode(JsonNode failureNode) {
        Failure failure = null;
        if (failureNode != null && !failureNode.isNull()) {
            try {
                failure = MAPPER.treeToValue(failureNode, Failure.class);
            } catch (Exception e) {
                throw new RuntimeException("Ошибка десериализации failure", e);
            }
        }
        return failure;
    }
}
