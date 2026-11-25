package by.syrnik.aimcp.runner.http;

import by.syrnik.aimcp.core.masking.MaskingException;
import by.syrnik.aimcp.core.masking.MaskingPort;
import by.syrnik.aimcp.runner.tools.Envelope;
import by.syrnik.aimcp.runner.tools.ErrorCode;
import by.syrnik.aimcp.runner.tools.ToolException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResponseWriterTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final MaskingPort IDENTITY = text -> text;
    private static final int LIMIT = 1024;

    private static Envelope.Reply okWith(String value) {
        ObjectNode result = MAPPER.createObjectNode().put("s", value);
        return Envelope.ok(result, IDENTITY);
    }

    private static int size(Envelope.Reply reply) throws Exception {
        return MAPPER.writeValueAsBytes(reply.body()).length;
    }

    private static JsonNode parse(byte[] body) throws Exception {
        return MAPPER.readTree(body);
    }

    private static void assertTooLarge(ResponseWriter.Response r) throws Exception {
        assertThat(r.httpStatus()).isEqualTo(500);
        JsonNode body = parse(r.body());
        assertThat(body.get("ok").booleanValue()).isFalse();
        assertThat(body.at("/error/code").textValue()).isEqualTo("response_too_large");
        assertThat(body.at("/error/message").textValue()).isEqualTo(ResponseWriter.TOO_LARGE_MESSAGE);
        assertThat(body.has("result")).isFalse();
    }

    @Test
    void writesOkReplyAsUtf8Json() throws Exception {
        Envelope.Reply reply = okWith("привет");

        ResponseWriter.Response r = new ResponseWriter(MAPPER, LIMIT, IDENTITY).write(reply);

        assertThat(r.httpStatus()).isEqualTo(200);
        assertThat(r.body()).isEqualTo(MAPPER.writeValueAsBytes(reply.body()));
        assertThat(new String(r.body(), StandardCharsets.UTF_8)).contains("привет");
    }

    @Test
    void keepsErrorStatus() throws Exception {
        Envelope.Reply reply = Envelope.error(new ToolException(ErrorCode.NOT_FOUND, "нет"), IDENTITY);

        ResponseWriter.Response r = new ResponseWriter(MAPPER, LIMIT, IDENTITY).write(reply);

        assertThat(r.httpStatus()).isEqualTo(404);
        assertThat(parse(r.body())).isEqualTo(reply.body());
    }

    @Test
    void exactLimitPassesOneByteMoreFails() throws Exception {
        Envelope.Reply base = okWith("");
        int overhead = size(base);
        Envelope.Reply exact = okWith("a".repeat(LIMIT - overhead));
        Envelope.Reply over = okWith("a".repeat(LIMIT - overhead + 1));
        assertThat(size(exact)).isEqualTo(LIMIT);

        ResponseWriter w = new ResponseWriter(MAPPER, LIMIT, IDENTITY);

        assertThat(w.write(exact).httpStatus()).isEqualTo(200);
        assertTooLarge(w.write(over));
    }

    @Test
    void limitCountsUtf8BytesNotChars() throws Exception {
        int overhead = size(okWith(""));
        String cyrillic = "я".repeat(LIMIT - overhead);
        Envelope.Reply reply = okWith(cyrillic);
        assertThat(cyrillic.length() + overhead).isEqualTo(LIMIT);

        assertTooLarge(new ResponseWriter(MAPPER, LIMIT, IDENTITY).write(reply));
    }

    @Test
    void tooLargeBodyHasNoOriginalContent() throws Exception {
        Envelope.Reply reply = okWith("secret-" + "x".repeat(LIMIT));

        ResponseWriter.Response r = new ResponseWriter(MAPPER, LIMIT, IDENTITY).write(reply);

        assertTooLarge(r);
        assertThat(new String(r.body(), StandardCharsets.UTF_8)).doesNotContain("secret");
    }

    @Test
    void limitAppliesToErrorReplies() throws Exception {
        Envelope.Reply reply = Envelope.error(
                new ToolException(ErrorCode.TOOL_FAILED, "x".repeat(LIMIT)), IDENTITY);

        assertTooLarge(new ResponseWriter(MAPPER, LIMIT, IDENTITY).write(reply));
    }

    @Test
    void constructorRejectsLimitSmallerThanErrorBody() throws Exception {
        int errorSize = MAPPER.writeValueAsBytes(Envelope.error(
                new ToolException(ErrorCode.RESPONSE_TOO_LARGE, ResponseWriter.TOO_LARGE_MESSAGE), IDENTITY).body()).length;

        assertThatThrownBy(() -> new ResponseWriter(MAPPER, errorSize - 1, IDENTITY))
                .isInstanceOf(IllegalArgumentException.class);

        ResponseWriter w = new ResponseWriter(MAPPER, errorSize, IDENTITY);
        assertTooLarge(w.write(okWith("x".repeat(errorSize))));
    }

    @Test
    void constructorRejectsNulls() {
        assertThatThrownBy(() -> new ResponseWriter(null, LIMIT, IDENTITY)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ResponseWriter(MAPPER, LIMIT, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void maskingFailureInConstructorGivesMaskingFailedBody() throws Exception {
        MaskingPort failing = text -> {
            throw new MaskingException("сбой");
        };

        ResponseWriter.Response r = new ResponseWriter(MAPPER, LIMIT, failing).write(okWith("x".repeat(LIMIT)));

        assertThat(r.httpStatus()).isEqualTo(500);
        assertThat(parse(r.body()).at("/error/code").textValue()).isEqualTo("masking_failed");
    }
}
