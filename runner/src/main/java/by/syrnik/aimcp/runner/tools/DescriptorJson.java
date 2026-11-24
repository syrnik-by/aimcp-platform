package by.syrnik.aimcp.runner.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public final class DescriptorJson {
    private DescriptorJson() {}

    public static ObjectNode write(ToolDescriptor d, ObjectMapper mapper) {
        ObjectNode root = mapper.createObjectNode();
        // TODO name, description
        inputSchema(root.putObject("inputSchema"), d.params());
        // TODO readOnly, idempotent
        return root;
    }

    private static void inputSchema(ObjectNode schema, List<Param> params) {
        // TODO type: object
        ObjectNode properties = schema.putObject("properties");
        for (Param p : params) {
            param(properties.putObject(p.name()), p);
        }
        // TODO required — только если есть обязательные
        // TODO additionalProperties: false
    }

    private static void param(ObjectNode node, Param p) {
        if (p instanceof StringParam s) {
            // TODO type, description, stringRules(node, s.rules())
        } else if (p instanceof IntegerParam i) {
            // TODO
        } else if (p instanceof BooleanParam b) {
            // TODO
        } else if (p instanceof StringArrayParam a) {
            // TODO type: array, description, maxItems (с умолчанием),
            //      items = отдельный объект {type: string + stringRules}
        }
    }

    private static void stringRules(ObjectNode node, StringRules r) {
        // TODO minLength, maxLength (с умолчанием 256), pattern, enum
    }
}
