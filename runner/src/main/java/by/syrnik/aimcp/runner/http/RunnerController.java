package by.syrnik.aimcp.runner.http;

import by.syrnik.aimcp.core.masking.MaskingPort;
import by.syrnik.aimcp.runner.tools.*;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.LoggerFactory;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Objects;

import static org.springframework.http.MediaType.APPLICATION_JSON;

@RequestMapping("/v1")
public class RunnerController {

    private static final org.slf4j.Logger log = LoggerFactory.getLogger(RunnerController.class);

    private final ToolRegistry registry;
    private final ObjectMapper mapper;
    private final MaskingPort masking;
    private final ResponseWriter writer;
    private final String project;
    private final String agentVersion;
    private final int maxRequestBytes;

    public RunnerController(ToolRegistry registry, ObjectMapper mapper, MaskingPort masking, ResponseWriter writer, String project, String agentVersion, int maxRequestBytes) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.masking = Objects.requireNonNull(masking, "masking");
        this.writer = Objects.requireNonNull(writer, "writer");
        this.project = Objects.requireNonNull(project, "project");
        this.agentVersion = Objects.requireNonNull(agentVersion, "agentVersion");
        if (maxRequestBytes >= 1) {
            this.maxRequestBytes = maxRequestBytes;
        } else {
            throw new IllegalArgumentException("неверно указан лимит размера запроса");
        }
    }

    @GetMapping("/info")
    public ResponseEntity<byte[]> info() {
        ObjectNode root = mapper.createObjectNode();
        ArrayNode contractVersions = root.putArray("contractVersions");
        contractVersions.add(1);
        root.put("agentVersion", agentVersion);
        root.put("project", project);
        ArrayNode arrayNode = root.putArray("tools");
        for (Tool tool : registry.all()) {
            arrayNode.add(tool.descriptor().name());
        }
        return respond(Envelope.ok(root, masking));
    }

    @GetMapping("/tools")
    public ResponseEntity<byte[]> tools() {
        ObjectNode root = mapper.createObjectNode();
        ArrayNode tools = root.putArray("tools");
        for (Tool tool : registry.all()) {
            tools.add(DescriptorJson.write(tool.descriptor(), mapper));
        }
        return respond(Envelope.ok(root, masking));
    }

    @PostMapping(value = "/tools/{name}")
    public ResponseEntity<byte[]> call(@RequestHeader(value = "Content-Type", required = false) String contentType,
                                       @PathVariable String name,
                                       InputStream in) {

        try {
            if (!MediaType.parseMediaType(contentType).isCompatibleWith(APPLICATION_JSON)) {
                throw new ToolException(ErrorCode.INVALID_REQUEST, "неверный Content-Type");
            }
            Tool tool = registry.find(name).orElseThrow(() -> new ToolException(ErrorCode.UNKNOWN_TOOL, "инструмент не найден"));
            JsonNode args = parse(in.readNBytes(maxRequestBytes + 1));
            Map<String, Object> valid = ArgsValidator.validate(tool.descriptor(), args);
            return respond(Envelope.ok(tool.call(valid), masking));
        } catch (ToolException e) {
            return respond(Envelope.error(e, masking));
        } catch (InvalidMediaTypeException e) {
            return respond(Envelope.error(new ToolException(ErrorCode.INVALID_REQUEST, "неверный Content-Type"), masking));
        } catch (IOException e) {
            return respond(Envelope.error(new ToolException(ErrorCode.INVALID_REQUEST, "не удалось прочитать тело запроса"), masking));
        } catch (RuntimeException e) {
            log.error("Ошибка вызова инструмента {} : {}", name, e.getClass().getName());
            return respond(Envelope.error(new ToolException(ErrorCode.INTERNAL, ""), masking));
        }
    }

    private JsonNode parse(byte[] body) {
        if (body.length > maxRequestBytes) {
            throw new ToolException(ErrorCode.REQUEST_TOO_LARGE, "лимит запроса превышен");
        }
        if (body == null || body.length == 0) {
            throw new ToolException(ErrorCode.INVALID_REQUEST, "тело пустое");
        }
        JsonNode bodyNode;
        try {
            bodyNode = mapper.reader()
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .with(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                    .readTree(body);
        } catch (IOException e) {
            throw new ToolException(ErrorCode.INVALID_REQUEST, "тело не JSON");
        }

        if (!bodyNode.isObject()) {
            throw new ToolException(ErrorCode.INVALID_REQUEST, "тело должно быть объектом");
        }

        return bodyNode;
    }

    private ResponseEntity<byte[]> respond(Envelope.Reply reply) {
        return writer.toEntity(reply);
    }
}
