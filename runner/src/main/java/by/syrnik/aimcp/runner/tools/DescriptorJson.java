package by.syrnik.aimcp.runner.tools;

import by.syrnik.aimcp.runner.tools.schema.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.HashSet;
import java.util.List;

public final class DescriptorJson {
    private DescriptorJson() {
    }

    public static ObjectNode write(ToolDescriptor toolDescriptor, ObjectMapper mapper) {
        ObjectNode root = mapper.createObjectNode();
        root.put("name", toolDescriptor.name());
        root.put("description", toolDescriptor.description());
        inputSchema(root.putObject("inputSchema"), toolDescriptor.params());
        root.put("readOnly", toolDescriptor.readOnly());
        root.put("idempotent", toolDescriptor.idempotent());
        return root;
    }

    private static void inputSchema(ObjectNode schema, List<Param> params) {
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        HashSet<String> required = new HashSet<>();
        for (Param p : params) {
            param(properties.putObject(p.name()), p);
            if (p.required()) {
                required.add(p.name());
            }
        }
        ArrayNode arrayNode = schema.putArray("required");
        for (String s : required) {
            arrayNode.add(s);
        }
        schema.put("additionalProperties", false);
    }

    private static void param(ObjectNode node, Param p) {
        if (p instanceof StringParam s) {
            node.put("type", "string");
            node.put("description", s.description());
            stringRules(node, s.rules());
        } else if (p instanceof IntegerParam i) {
            node.put("type", "integer");
            node.put("description", i.description());
            node.put("minimum", i.minimum());
            node.put("maximum", i.maximum());
        } else if (p instanceof BooleanParam b) {
            node.put("type", "boolean");
            node.put("description", b.description());
        } else if (p instanceof StringArrayParam a) {
            node.put("type", "array");
            node.put("description", a.description());
            ObjectNode items = node.putObject("items");
            items.put("type", "string");
            stringRules(items, a.items());
        }
    }

    private static void stringRules(ObjectNode node, StringRules r) {
        if (r.minLength() != null) {
            node.put("minLength", r.minLength());
        }
        node.put("maxLength", r.maxLength());
        node.put("pattern", r.pattern());
        if (!r.enumValues().isEmpty()) {
            ArrayNode arrayNode = node.putArray("enum");
            for (String s : r.enumValues()) {
                arrayNode.add(s);
            }
        }
    }
}
