package by.syrnik.aimcp.runner.http;

import by.syrnik.aimcp.core.masking.MaskingPort;
import by.syrnik.aimcp.runner.tools.Tool;
import by.syrnik.aimcp.runner.tools.ToolDescriptor;
import by.syrnik.aimcp.runner.tools.ToolRegistry;
import by.syrnik.aimcp.runner.tools.schema.StringParam;
import by.syrnik.aimcp.runner.tools.schema.StringRules;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ErrorAdviceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int RESPONSE_LIMIT = 1 << 20;
    private static final int REQUEST_LIMIT = 64;
    private static final String SECRET_PROJECT = "proj-SECRET";

    private static final MaskingPort MASKING = text -> {
        if (text.contains("SECRET")) {
            throw new IllegalStateException("утечка " + text);
        }
        return text;
    };

    private int calls;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        calls = 0;
        mvc = mvc("at");
    }

    private MockMvc mvc(String project) {
        Tool echo = new Tool() {
            private final ToolDescriptor descriptor = new ToolDescriptor("echo", "d",
                    List.of(new StringParam("text", "d", true, StringRules.NONE)), true, true);

            @Override
            public ToolDescriptor descriptor() {
                return descriptor;
            }

            @Override
            public ObjectNode call(Map<String, Object> args) {
                calls++;
                return JsonNodeFactory.instance.objectNode().put("len", ((String) args.get("text")).length());
            }
        };
        ResponseWriter writer = new ResponseWriter(MAPPER, RESPONSE_LIMIT, MASKING);
        RunnerController controller = new RunnerController(new ToolRegistry(List.of(echo)), MAPPER, MASKING,
                writer, project, "0.9.1", REQUEST_LIMIT);
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ErrorAdvice(writer, MASKING))
                .addDispatcherServletCustomizer(d -> d.setThrowExceptionIfNoHandlerFound(true))
                .build();
    }

    private static String bodyOfSize(int size) {
        String prefix = "{\"text\":\"";
        String suffix = "\"}";
        String body = prefix + "a".repeat(size - prefix.length() - suffix.length()) + suffix;
        assertThat(body.getBytes(StandardCharsets.UTF_8)).hasSize(size);
        return body;
    }

    private ResultActions postEcho(String body) throws Exception {
        return mvc.perform(post("/v1/tools/echo").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static void assertEnvelopeError(ResultActions r, int status, String code) throws Exception {
        r.andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.error.code").value(code))
                .andExpect(jsonPath("$.error.message").isString())
                .andExpect(jsonPath("$.result").doesNotExist());
    }

    // --- неизвестный путь ---

    @Test
    void unknownPathIsNotFoundInEnvelope() throws Exception {
        for (String path : List.of("/v2/info", "/v1/tool", "/v1/tools/echo/extra", "/", "/v1")) {
            mvc.perform(get(path))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.ok").value(false))
                    .andExpect(jsonPath("$.result").doesNotExist());
        }
    }

    @Test
    void unknownPathUsesNotFoundCode() throws Exception {
        assertEnvelopeError(mvc.perform(get("/v2/info")), 404, "not_found");
    }

    @Test
    void unknownPathDoesNotEchoPath() throws Exception {
        String body = mvc.perform(get("/v1/zzz-probe")).andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("zzz-probe");
    }

    // --- чужой метод ---

    @Test
    void getOnToolCallIsMethodNotAllowedWithAllow() throws Exception {
        ResultActions r = mvc.perform(get("/v1/tools/echo"));

        assertEnvelopeError(r, 405, "method_not_allowed");
        r.andExpect(header().string("Allow", "POST"));
        assertThat(calls).isZero();
    }

    @Test
    void postOnInfoIsMethodNotAllowedWithAllow() throws Exception {
        ResultActions r = mvc.perform(post("/v1/info").contentType(MediaType.APPLICATION_JSON).content("{}"));

        assertEnvelopeError(r, 405, "method_not_allowed");
        r.andExpect(header().string("Allow", "GET"));
    }

    @Test
    void otherMethodsAreMethodNotAllowed() throws Exception {
        assertEnvelopeError(mvc.perform(delete("/v1/tools")), 405, "method_not_allowed");
        assertEnvelopeError(mvc.perform(put("/v1/tools/echo").contentType(MediaType.APPLICATION_JSON).content("{}")),
                405, "method_not_allowed");
    }

    // --- лимит тела запроса ---

    @Test
    void bodyExactlyAtLimitIsAccepted() throws Exception {
        postEcho(bodyOfSize(REQUEST_LIMIT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.len").value(REQUEST_LIMIT - 11));

        assertThat(calls).isEqualTo(1);
    }

    @Test
    void bodyOneByteOverLimitIsRequestTooLarge() throws Exception {
        assertEnvelopeError(postEcho(bodyOfSize(REQUEST_LIMIT + 1)), 413, "request_too_large");

        assertThat(calls).isZero();
    }

    @Test
    void hugeBodyIsRequestTooLarge() throws Exception {
        assertEnvelopeError(postEcho(bodyOfSize(REQUEST_LIMIT * 1000)), 413, "request_too_large");

        assertThat(calls).isZero();
    }

    @Test
    void oversizedGarbageIsTooLargeNotInvalid() throws Exception {
        assertEnvelopeError(postEcho("x".repeat(REQUEST_LIMIT + 1)), 413, "request_too_large");
    }

    @Test
    void negativeRequestLimitIsRejected() {
        ResponseWriter writer = new ResponseWriter(MAPPER, RESPONSE_LIMIT, MASKING);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new RunnerController(
                        new ToolRegistry(List.of()), MAPPER, MASKING, writer, "at", "0.9.1", -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- непредвиденная ошибка вне try контроллера ---

    @Test
    void unexpectedExceptionOutsideToolCallIsInternalWithoutDetails() throws Exception {
        ResultActions r = mvc(SECRET_PROJECT).perform(get("/v1/info"));

        assertEnvelopeError(r, 500, "internal");
        String body = r.andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("SECRET", "утечка", "IllegalStateException");
    }
}
