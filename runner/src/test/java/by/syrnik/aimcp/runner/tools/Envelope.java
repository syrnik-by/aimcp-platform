package by.syrnik.aimcp.runner.tools;

import by.syrnik.aimcp.core.masking.MaskingException;
import by.syrnik.aimcp.core.masking.MaskingPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

public final class Envelope {
    static final String MASKING_FAILED_MESSAGE = "ответ не удалось замаскировать";
    static final String INTERNAL_MESSAGE = "внутренняя ошибка";

    private static final JsonNodeFactory F = JsonNodeFactory.instance;

    public record Reply(int httpStatus, ObjectNode body) {
    }

    private Envelope() {
    }

    public static Reply ok(ObjectNode result, MaskingPort masking) {
        Objects.requireNonNull(result, "result");
        JsonNode masked;
        try {
            masked = maskNode(result, masking);
        } catch (MaskingException e) {
            return failure(ErrorCode.MASKING_FAILED, MASKING_FAILED_MESSAGE);
        }
        ObjectNode root = F.objectNode();
        root.put("ok", true);
        root.set("result", masked);

        return new Reply(200, root);
    }

    public static Reply error(ToolException e, MaskingPort masking) {
        if (e.code() == ErrorCode.INTERNAL) {
            return failure(ErrorCode.INTERNAL, INTERNAL_MESSAGE);
        }
        if (e.code() == ErrorCode.MASKING_FAILED) {
            return failure(ErrorCode.MASKING_FAILED, MASKING_FAILED_MESSAGE);
        }
        return failure(e.code(), masking.mask(e.getMessage()));
    }

    private static JsonNode maskNode(JsonNode node, MaskingPort masking) {
        if (node.isObject()) {
            ObjectNode objectNode = F.objectNode();
            for (Iterator<Map.Entry<String, JsonNode>> it = node.fields(); it.hasNext(); ) {
                Map.Entry<String, JsonNode> entry = it.next();
                objectNode.set(entry.getKey(), maskNode(entry.getValue(), masking));
            }
            return objectNode;
        }
        if (node.isTextual()) {
            return F.textNode(masking.mask(node.textValue()));
        }
        if (node.isArray()) {
            ArrayNode arrayNode = F.arrayNode();
            for (Iterator<JsonNode> iter = node.elements(); iter.hasNext(); ) {
                JsonNode n = iter.next();
                arrayNode.add(maskNode(n, masking));
            }
            return arrayNode;
        }
        if (node.isBoolean() || node.isNumber() || node.isNull()) {
            return F.textNode(node.textValue());
        }
        throw new IllegalStateException("неподдерживаемый узел " + node.getNodeType());
    }

    private static Reply failure(ErrorCode code, String message) {
        ObjectNode root = F.objectNode();
        root.put("ok", false);
        ObjectNode error = root.putObject("error");
        error.put("code", code.code());
        error.put("message", message);

        return new Reply(code.httpStatus(), root);
    }
}
