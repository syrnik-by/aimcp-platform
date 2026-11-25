package by.syrnik.aimcp.runner.http;

import by.syrnik.aimcp.core.masking.MaskingPort;
import by.syrnik.aimcp.runner.tools.Envelope;
import by.syrnik.aimcp.runner.tools.ErrorCode;
import by.syrnik.aimcp.runner.tools.ToolException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Objects;

import static by.syrnik.aimcp.runner.tools.ErrorCode.RESPONSE_TOO_LARGE;

public final class ResponseWriter {
    static final String TOO_LARGE_MESSAGE = "ответ больше лимита";

    public record Response(int httpStatus, byte[] body) {}

    private final ObjectMapper mapper;
    private final int maxBytes;
    private final byte[] tooLargeBody;

    public ResponseWriter(ObjectMapper mapper, int maxBytes, MaskingPort masking) {
        Objects.requireNonNull(mapper, "mapper");
        Objects.requireNonNull(masking, "masking");
        this.mapper = mapper;
        this.tooLargeBody = toBytes(Envelope.error(new ToolException(RESPONSE_TOO_LARGE, TOO_LARGE_MESSAGE), masking).body());

        if (maxBytes < this.tooLargeBody.length) {
            throw new IllegalArgumentException("лимит ответа меньше размера ответа об ошибке");
        } else {
            this.maxBytes = maxBytes;
        }
    }

    public Response write(Envelope.Reply reply) {
        byte[] bytes = toBytes(reply.body());
        if (bytes.length > maxBytes) {
            return new Response(RESPONSE_TOO_LARGE.httpStatus(), tooLargeBody);
        } else {
            return new Response(reply.httpStatus(), bytes);
        }
    }

    private byte[] toBytes(JsonNode body) {
        try {
            return mapper.writeValueAsBytes(body);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("не удалось сериализовать ответ");
        }
    }
}
