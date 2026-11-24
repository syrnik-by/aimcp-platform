package by.syrnik.aimcp.runner.tools;

import by.syrnik.aimcp.runner.tools.schema.BooleanParam;
import by.syrnik.aimcp.runner.tools.schema.IntegerParam;
import by.syrnik.aimcp.runner.tools.schema.StringArrayParam;
import by.syrnik.aimcp.runner.tools.schema.StringParam;
import by.syrnik.aimcp.runner.tools.schema.StringRules;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DescriptorJsonTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void matchesContractExample() throws Exception {
        ToolDescriptor checkEnv = new ToolDescriptor(
                "check_env",
                "Проверяет доступность стенда проекта",
                List.of(new StringParam("stand", "Имя стенда", true,
                        new StringRules(null, 64, null, List.of()))),
                true,
                true);

        JsonNode expected = mapper.readTree("""
                {
                  "name": "check_env",
                  "description": "Проверяет доступность стенда проекта",
                  "inputSchema": {
                    "type": "object",
                    "properties": { "stand": { "type": "string", "description": "Имя стенда", "maxLength": 64 } },
                    "required": ["stand"],
                    "additionalProperties": false
                  },
                  "readOnly": true,
                  "idempotent": true
                }
                """);

        assertThat(asWritten(DescriptorJson.write(checkEnv, mapper))).isEqualTo(expected);
    }

    @Test
    void writesAllParamTypesAndRules() throws Exception {
        ToolDescriptor tool = new ToolDescriptor(
                "run_tests",
                "Запускает тесты",
                List.of(
                        new StringParam("stand", "Стенд", true,
                                new StringRules(2, 32, "[a-z]+", List.of("dev", "test"))),
                        new IntegerParam("timeout_sec", "Таймаут", false, 1L, 600L),
                        new BooleanParam("verbose", "Подробно", false),
                        new StringArrayParam("tags", "Теги", false, 10,
                                new StringRules(null, 20, "[a-z_]+", List.of()))),
                false,
                false);

        JsonNode expected = mapper.readTree("""
                {
                  "name": "run_tests",
                  "description": "Запускает тесты",
                  "inputSchema": {
                    "type": "object",
                    "properties": {
                      "stand": {
                        "type": "string", "description": "Стенд",
                        "minLength": 2, "maxLength": 32, "pattern": "[a-z]+", "enum": ["dev", "test"]
                      },
                      "timeout_sec": { "type": "integer", "description": "Таймаут", "minimum": 1, "maximum": 600 },
                      "verbose": { "type": "boolean", "description": "Подробно" },
                      "tags": {
                        "type": "array", "description": "Теги", "maxItems": 10,
                        "items": { "type": "string", "maxLength": 20, "pattern": "[a-z_]+" }
                      }
                    },
                    "required": ["stand"],
                    "additionalProperties": false
                  },
                  "readOnly": false,
                  "idempotent": false
                }
                """);

        assertThat(asWritten(DescriptorJson.write(tool, mapper))).isEqualTo(expected);
    }

    @Test
    void writesDefaultsAndOmitsUnsetRules() throws Exception {
        ToolDescriptor tool = new ToolDescriptor(
                "list_runs",
                "Список прогонов",
                List.of(
                        new StringParam("filter", "Фильтр", false, StringRules.NONE),
                        new IntegerParam("limit", "Лимит", false, null, null),
                        new StringArrayParam("ids", "Идентификаторы", false, null, StringRules.NONE)),
                true,
                true);

        JsonNode expected = mapper.readTree("""
                {
                  "type": "object",
                  "properties": {
                    "filter": { "type": "string", "description": "Фильтр", "maxLength": 256 },
                    "limit": { "type": "integer", "description": "Лимит" },
                    "ids": {
                      "type": "array", "description": "Идентификаторы", "maxItems": 100,
                      "items": { "type": "string", "maxLength": 256 }
                    }
                  },
                  "additionalProperties": false
                }
                """);

        ObjectNode json = DescriptorJson.write(tool, mapper);

        assertThat(asWritten(json.get("inputSchema"))).isEqualTo(expected);
        assertThat(json.toString()).doesNotContain("null");
    }

    @Test
    void omitsRequiredWhenNothingRequired() {
        ToolDescriptor noParams = new ToolDescriptor("ping", "Пинг", List.of(), true, true);
        ToolDescriptor optionalOnly = new ToolDescriptor("ping", "Пинг",
                List.of(new BooleanParam("verbose", "Подробно", false)), true, true);

        JsonNode schema = DescriptorJson.write(noParams, mapper).get("inputSchema");
        assertThat(schema.has("required")).isFalse();
        assertThat(schema.get("properties").isObject()).isTrue();
        assertThat(schema.get("properties").isEmpty()).isTrue();
        assertThat(schema.get("additionalProperties").asBoolean(true)).isFalse();

        assertThat(DescriptorJson.write(optionalOnly, mapper).get("inputSchema").has("required")).isFalse();
    }

    @Test
    void keepsDeclarationOrder() {
        ToolDescriptor tool = new ToolDescriptor("deploy", "Деплой", List.of(
                new StringParam("zeta", "z", true, StringRules.NONE),
                new BooleanParam("alpha", "a", false),
                new StringParam("mid", "m", true, StringRules.NONE),
                new IntegerParam("beta", "b", true, null, null)), false, false);

        JsonNode schema = DescriptorJson.write(tool, mapper).get("inputSchema");

        assertThat(schema.get("properties").fieldNames())
                .toIterable().containsExactly("zeta", "alpha", "mid", "beta");
        assertThat(schema.get("required"))
                .extracting(JsonNode::asText).containsExactly("zeta", "mid", "beta");
    }

    @Test
    void keepsTopLevelFieldOrder() {
        ToolDescriptor tool = new ToolDescriptor("ping", "Пинг", List.of(), true, true);

        assertThat(DescriptorJson.write(tool, mapper).fieldNames()).toIterable()
                .containsExactly("name", "description", "inputSchema", "readOnly", "idempotent");
    }

    // Long-поля дают LongNode, а парсер читает мелкие числа как IntNode; сравниваем то, что уйдёт по сети.
    private JsonNode asWritten(JsonNode node) throws Exception {
        return mapper.readTree(mapper.writeValueAsString(node));
    }
}
