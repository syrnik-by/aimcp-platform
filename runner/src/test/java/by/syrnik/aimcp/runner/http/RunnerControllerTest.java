package by.syrnik.aimcp.runner.http;

import by.syrnik.aimcp.core.masking.MaskingException;
import by.syrnik.aimcp.core.masking.MaskingPort;
import by.syrnik.aimcp.runner.tools.*;
import by.syrnik.aimcp.runner.tools.schema.IntegerParam;
import by.syrnik.aimcp.runner.tools.schema.Param;
import by.syrnik.aimcp.runner.tools.schema.StringParam;
import by.syrnik.aimcp.runner.tools.schema.StringRules;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RunnerControllerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int LIMIT = 1 << 20;
    private static final String PROJECT = "at";
    private static final String AGENT_VERSION = "0.9.1";

    private static final MaskingPort MARK = text -> {
        if (text.contains("BOOM")) {
            throw new MaskingException("сбой");
        }
        return "M(" + text + ")";
    };

    private FakeTool echo;
    private FakeTool failing;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        echo = new FakeTool(descriptor("echo", true,
                new StringParam("text", "что вернуть", true, StringRules.NONE),
                new IntegerParam("times", "сколько раз", false, null, null)),
                args -> JsonNodeFactory.instance.objectNode()
                        .put("backend", "db-" + args.get("text")));

        failing = new FakeTool(descriptor("check_env", true,
                new StringParam("stand", "имя стенда", false, StringRules.NONE)),
                args -> {
                    throw new ToolException(ErrorCode.NOT_FOUND, "стенд " + args.get("stand") + " не найден");
                });

        mvc = mvc(echo, failing);
    }

    private static ToolDescriptor descriptor(String name, boolean readOnly, Param... params) {
        return new ToolDescriptor(name, "d", List.of(params), readOnly, !readOnly);
    }

    private MockMvc mvc(Tool... tools) {
        RunnerController controller = new RunnerController(
                new ToolRegistry(List.of(tools)), MAPPER, MARK,
                new ResponseWriter(MAPPER, LIMIT, MARK), PROJECT, AGENT_VERSION);
        return MockMvcBuilders.standaloneSetup(controller).build();
    }

    private static String json(String s) {
        return s.replace('\'', '"');
    }

    // --- GET /v1/info ---

    @Test
    void infoReturnsEnvelopeWithContractVersions() throws Exception {
        mvc.perform(get("/v1/info"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.result.contractVersions").isArray())
                .andExpect(jsonPath("$.result.contractVersions.length()").value(1))
                .andExpect(jsonPath("$.result.contractVersions[0]").value(1))
                .andExpect(jsonPath("$.result.agentVersion").value("M(" + AGENT_VERSION + ")"))
                .andExpect(jsonPath("$.result.project").value("M(" + PROJECT + ")"))
                .andExpect(jsonPath("$.result.tools.length()").value(2))
                .andExpect(jsonPath("$.result.tools[0]").value("M(echo)"))
                .andExpect(jsonPath("$.result.tools[1]").value("M(check_env)"))
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    // --- GET /v1/tools ---

    @Test
    void toolsListsDescriptorsInRegistrationOrder() throws Exception {
        mvc.perform(get("/v1/tools"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.result.tools.length()").value(2))
                .andExpect(jsonPath("$.result.tools[0].name").value("M(echo)"))
                .andExpect(jsonPath("$.result.tools[0].inputSchema.type").value("M(object)"))
                .andExpect(jsonPath("$.result.tools[0].inputSchema.properties.text.type").value("M(string)"))
                .andExpect(jsonPath("$.result.tools[0].inputSchema.properties.times.type").value("M(integer)"))
                .andExpect(jsonPath("$.result.tools[0].inputSchema.additionalProperties").value(false))
                .andExpect(jsonPath("$.result.tools[1].name").value("M(check_env)"))
                .andExpect(jsonPath("$.result.tools[1].readOnly").value(true));
    }

    // --- POST /v1/tools/{name} ---

    @Test
    void callReturnsToolResult() throws Exception {
        mvc.perform(post("/v1/tools/echo").contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'text':'alpha'}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.result.backend").value("M(db-alpha)"));
    }

    @Test
    void callPassesValidatedArgumentsInDescriptorOrder() throws Exception {
        mvc.perform(post("/v1/tools/echo").contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'times':2,'text':'alpha'}")))
                .andExpect(status().isOk());

        assertThat(echo.calls()).isEqualTo(1);
        assertThat(echo.lastArgs()).containsOnlyKeys("text", "times");
        assertThat(echo.lastArgs()).containsEntry("text", "alpha").containsEntry("times", 2L);
        assertThat(echo.lastArgs().keySet()).containsExactly("text", "times");
    }

    @Test
    void callWithEmptyObjectWorksWhenNoRequiredArguments() throws Exception {
        mvc.perform(post("/v1/tools/check_env").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());

        assertThat(failing.calls()).isEqualTo(1);
    }

    @Test
    void unknownToolIsNotFoundAndToolIsNotCalled() throws Exception {
        mvc.perform(post("/v1/tools/nope").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.error.code").value("unknown_tool"))
                .andExpect(jsonPath("$.result").doesNotExist());

        assertThat(echo.calls()).isZero();
        assertThat(failing.calls()).isZero();
    }

    @Test
    void unknownToolIsReportedEvenForInvalidBody() throws Exception {
        mvc.perform(post("/v1/tools/nope").contentType(MediaType.APPLICATION_JSON).content("не json"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("unknown_tool"));
    }

    @Test
    void emptyBodyIsInvalidRequest() throws Exception {
        mvc.perform(post("/v1/tools/echo").contentType(MediaType.APPLICATION_JSON).content(""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.error.code").value("invalid_request"));

        assertThat(echo.calls()).isZero();
    }

    @Test
    void malformedJsonIsInvalidRequest() throws Exception {
        mvc.perform(post("/v1/tools/echo").contentType(MediaType.APPLICATION_JSON).content("{'text':"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("invalid_request"));

        assertThat(echo.calls()).isZero();
    }

    @Test
    void jsonNullBodyIsInvalidRequest() throws Exception {
        mvc.perform(post("/v1/tools/echo").contentType(MediaType.APPLICATION_JSON).content("null"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("invalid_request"));
    }

    @Test
    void nonObjectBodyIsInvalidRequest() throws Exception {
        mvc.perform(post("/v1/tools/echo").contentType(MediaType.APPLICATION_JSON).content("[]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("invalid_request"));

        assertThat(echo.calls()).isZero();
    }

    @Test
    void trailingTokensAreInvalidRequest() throws Exception {
        mvc.perform(post("/v1/tools/echo").contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'text':'a'} {'text':'b'}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("invalid_request"));

        assertThat(echo.calls()).isZero();
    }

    @Test
    void duplicateKeysAreInvalidRequest() throws Exception {
        mvc.perform(post("/v1/tools/echo").contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'text':'a','text':'b'}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("invalid_request"));

        assertThat(echo.calls()).isZero();
    }

    @Test
    void invalidArgumentsAreRejectedByEnvelope() throws Exception {
        mvc.perform(post("/v1/tools/echo").contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'text':'a','extra':1}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("invalid_arguments"))
                .andExpect(jsonPath("$.result").doesNotExist());

        assertThat(echo.calls()).isZero();
    }

    @Test
    void wrongContentTypeIsInvalidRequestInEnvelope() throws Exception {
        mvc.perform(post("/v1/tools/echo").contentType(MediaType.TEXT_PLAIN).content(json("{'text':'a'}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.error.code").value("invalid_request"));

        assertThat(echo.calls()).isZero();
    }

    @Test
    void missingContentTypeIsInvalidRequestInEnvelope() throws Exception {
        mvc.perform(post("/v1/tools/echo").content(json("{'text':'a'}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.error.code").value("invalid_request"));
    }

    @Test
    void jsonWithCharsetIsAccepted() throws Exception {
        mvc.perform(post("/v1/tools/echo").contentType("application/json;charset=UTF-8")
                        .content(json("{'text':'alpha'}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));
    }

    @Test
    void malformedContentTypeIsInvalidRequestWithoutEchoingHeader() throws Exception {
        String body = mvc.perform(post("/v1/tools/echo").header("Content-Type", "zzz-secret-header")
                        .content(json("{'text':'a'}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("invalid_request"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("zzz-secret-header");
        assertThat(echo.calls()).isZero();
    }

    // --- ошибки инструмента ---

    @Test
    void toolExceptionKeepsCodeAndMasksMessage() throws Exception {
        mvc.perform(post("/v1/tools/check_env").contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'stand':'stage'}")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.error.code").value("not_found"))
                .andExpect(jsonPath("$.error.message").value("M(стенд stage не найден)"))
                .andExpect(jsonPath("$.result").doesNotExist());
    }

    @Test
    void busyToolKeepsStatusAndMasksMessage() throws Exception {
        FakeTool busy = new FakeTool(descriptor("run_tests", false), args -> {
            throw new ToolException(ErrorCode.BUSY, "прогон уже идёт");
        });
        mvc(busy).perform(post("/v1/tools/run_tests").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("busy"))
                .andExpect(jsonPath("$.error.message").value("M(прогон уже идёт)"));
    }

    @Test
    void unexpectedExceptionBecomesInternalWithoutDetails() throws Exception {
        FakeTool broken = new FakeTool(descriptor("get_profile", true), args -> {
            throw new IllegalStateException("секретный путь /home/user/key.pem");
        });

        String body = mvc(broken)
                .perform(post("/v1/tools/get_profile").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.error.code").value("internal"))
                .andExpect(jsonPath("$.result").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("секретный", "key.pem", "/home/user", "IllegalStateException");
    }

    @Test
    void nullToolResultBecomesInternal() throws Exception {
        FakeTool nullResult = new FakeTool(descriptor("get_profile", true), args -> null);

        mvc(nullResult).perform(post("/v1/tools/get_profile").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("internal"));
    }

    @Test
    void maskingFailureReturnsMaskingFailedWithoutContent() throws Exception {
        FakeTool leaky = new FakeTool(descriptor("collect_artifacts", true),
                args -> JsonNodeFactory.instance.objectNode().put("path", "BOOM"));

        String body = mvc(leaky)
                .perform(post("/v1/tools/collect_artifacts").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("masking_failed"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("BOOM", "сбой");
    }

    @Test
    void maskingFailureInToolErrorMessageHidesMessage() throws Exception {
        FakeTool leakyError = new FakeTool(descriptor("get_profile", true), args -> {
            throw new ToolException(ErrorCode.TOOL_FAILED, "BOOM");
        });

        mvc(leakyError).perform(post("/v1/tools/get_profile").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("masking_failed"));
    }

    // --- каркас ---

    @Test
    void responseTooLargeIsReportedByWriter() throws Exception {
        FakeTool big = new FakeTool(descriptor("read_run_logs", true),
                args -> JsonNodeFactory.instance.objectNode().put("log", "x".repeat(4096)));

        RunnerController controller = new RunnerController(new ToolRegistry(List.of(big)), MAPPER, MARK,
                new ResponseWriter(MAPPER, 512, MARK), PROJECT, AGENT_VERSION);

        MockMvcBuilders.standaloneSetup(controller).build()
                .perform(post("/v1/tools/read_run_logs").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("response_too_large"));
    }

    private static final class FakeTool implements Tool {
        private final ToolDescriptor descriptor;
        private final Handler handler;
        private int calls;
        private Map<String, Object> lastArgs;

        FakeTool(ToolDescriptor descriptor, Handler handler) {
            this.descriptor = descriptor;
            this.handler = handler;
        }

        @Override
        public ToolDescriptor descriptor() {
            return descriptor;
        }

        @Override
        public ObjectNode call(Map<String, Object> args) {
            calls++;
            lastArgs = args;
            return handler.handle(args);
        }

        int calls() {
            return calls;
        }

        Map<String, Object> lastArgs() {
            return lastArgs;
        }
    }

    @FunctionalInterface
    private interface Handler {
        ObjectNode handle(Map<String, Object> args);
    }
}
