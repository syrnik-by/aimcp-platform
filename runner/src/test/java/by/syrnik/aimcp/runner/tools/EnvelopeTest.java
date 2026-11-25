package by.syrnik.aimcp.runner.tools;

import by.syrnik.aimcp.core.masking.MaskingException;
import by.syrnik.aimcp.core.masking.MaskingPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EnvelopeTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final MaskingPort MARK = text -> {
        if (text.contains("BOOM")) {
            throw new MaskingException("сбой");
        }
        return "M(" + text + ")";
    };

    private static ObjectNode json(String s) {
        try {
            return (ObjectNode) MAPPER.readTree(s.replace('\'', '"'));
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Test
    void okMasksAllStringsAndKeepsKeysAndScalars() {
        ObjectNode result = json("{'name':'db','port':5432,'ratio':0.5,'up':true,'none':null,"
                + "'nested':{'url':'x','tags':['a',1,false,null,{'k':'v'}]},'empty':{},'list':[]}");

        Envelope.Reply r = Envelope.ok(result, MARK);

        assertThat(r.httpStatus()).isEqualTo(200);
        assertThat(r.body()).isEqualTo(json("{'ok':true,'result':{'name':'M(db)','port':5432,'ratio':0.5,"
                + "'up':true,'none':null,'nested':{'url':'M(x)','tags':['M(a)',1,false,null,{'k':'M(v)'}]},"
                + "'empty':{},'list':[]}}"));
    }

    @Test
    void okKeepsFieldOrder() {
        Envelope.Reply r = Envelope.ok(json("{'b':'1','a':'2','c':'3'}"), MARK);

        assertThat(r.body().get("result").fieldNames()).toIterable().containsExactly("b", "a", "c");
    }

    @Test
    void okDoesNotModifyInput() {
        ObjectNode result = json("{'s':'x','arr':['y'],'o':{'z':'w'}}");
        ObjectNode copy = result.deepCopy();

        Envelope.ok(result, MARK);

        assertThat(result).isEqualTo(copy);
    }

    @Test
    void okMaskingFailureReturnsNoContent() {
        ObjectNode result = json("{'first':'secret-1','deep':{'arr':['secret-2','BOOM']}}");

        Envelope.Reply r = Envelope.ok(result, MARK);

        assertThat(r.httpStatus()).isEqualTo(500);
        assertThat(r.body()).isEqualTo(json(
                "{'ok':false,'error':{'code':'masking_failed','message':'" + Envelope.MASKING_FAILED_MESSAGE + "'}}"));
        assertThat(r.body().toString()).doesNotContain("secret", "сбой");
    }

    @Test
    void okRejectsPojoNode() {
        ObjectNode result = json("{}");
        result.putPOJO("raw", new Object());

        assertThatThrownBy(() -> Envelope.ok(result, MARK))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("неподдерживаемый узел POJO");
    }

    @Test
    void okRejectsBinaryNode() {
        ObjectNode result = json("{}");
        result.put("bin", new byte[]{1, 2});

        assertThatThrownBy(() -> Envelope.ok(result, MARK)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void okRejectsNullResult() {
        assertThatThrownBy(() -> Envelope.ok(null, MARK)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void okDoesNotSwallowOtherExceptions() {
        MaskingPort broken = text -> {
            throw new IllegalArgumentException("баг");
        };

        assertThatThrownBy(() -> Envelope.ok(json("{'s':'x'}"), broken))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @EnumSource(value = ErrorCode.class, names = {"INTERNAL", "MASKING_FAILED"}, mode = EnumSource.Mode.EXCLUDE)
    void errorMasksMessageAndUsesCodeStatus(ErrorCode code) {
        Envelope.Reply r = Envelope.error(new ToolException(code, "текст"), MARK);

        assertThat(r.httpStatus()).isEqualTo(code.httpStatus());
        assertThat(r.body()).isEqualTo(json(
                "{'ok':false,'error':{'code':'" + code.code() + "','message':'M(текст)'}}"));
    }

    @Test
    void errorInternalHidesMessage() {
        Envelope.Reply r = Envelope.error(new ToolException(ErrorCode.INTERNAL, "secret detail"), MARK);

        assertThat(r.httpStatus()).isEqualTo(500);
        assertThat(r.body().at("/error/code").textValue()).isEqualTo("internal");
        assertThat(r.body().at("/error/message").textValue()).isEqualTo(Envelope.INTERNAL_MESSAGE);
    }

    @Test
    void errorMaskingFailedUsesFixedMessage() {
        Envelope.Reply r = Envelope.error(new ToolException(ErrorCode.MASKING_FAILED, "secret detail"), MARK);

        assertThat(r.body().at("/error/code").textValue()).isEqualTo("masking_failed");
        assertThat(r.body().at("/error/message").textValue()).isEqualTo(Envelope.MASKING_FAILED_MESSAGE);
    }

    @Test
    void errorMaskingFailureOnMessageReturnsMaskingFailed() {
        Envelope.Reply r = Envelope.error(new ToolException(ErrorCode.TOOL_FAILED, "secret BOOM"), MARK);

        assertThat(r.httpStatus()).isEqualTo(500);
        assertThat(r.body()).isEqualTo(json(
                "{'ok':false,'error':{'code':'masking_failed','message':'" + Envelope.MASKING_FAILED_MESSAGE + "'}}"));
    }

    @Test
    void errorBodyHasOnlyContractFields() {
        JsonNode body = Envelope.error(new ToolException(ErrorCode.NOT_FOUND, "x"), MARK).body();

        assertThat(body.fieldNames()).toIterable().containsExactly("ok", "error");
        assertThat(body.get("error").fieldNames()).toIterable().containsExactly("code", "message");
    }

    @Test
    void toolExceptionRequiresMessage() {
        assertThatThrownBy(() -> new ToolException(ErrorCode.TOOL_FAILED, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ToolException(ErrorCode.TOOL_FAILED, null, new RuntimeException()))
                .isInstanceOf(NullPointerException.class);
    }
}
