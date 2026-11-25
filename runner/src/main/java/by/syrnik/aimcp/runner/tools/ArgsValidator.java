package by.syrnik.aimcp.runner.tools;

import by.syrnik.aimcp.runner.tools.schema.BooleanParam;
import by.syrnik.aimcp.runner.tools.schema.IntegerParam;
import by.syrnik.aimcp.runner.tools.schema.Param;
import by.syrnik.aimcp.runner.tools.schema.StringArrayParam;
import by.syrnik.aimcp.runner.tools.schema.StringParam;
import by.syrnik.aimcp.runner.tools.schema.StringRules;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public final class ArgsValidator {
    private ArgsValidator() {
    }

    public static Map<String, Object> validate(ToolDescriptor descriptor, JsonNode args) {
        if (args == null || !args.isObject()) {
            throw invalid("аргументы должны быть объектом");
        }

        Map<String, Param> byName = new HashMap<>();
        for (Param p : descriptor.params()) {
            byName.put(p.name(), p);
        }

        Iterator<String> names = args.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            if (!byName.containsKey(name)) {
                throw invalid(name + ": неизвестный аргумент");
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        for (Param p : descriptor.params()) {
            JsonNode value = args.get(p.name());
            if (value == null) {
                if (p.required()) {
                    throw invalid(p.name() + ": обязательный");
                }
                continue;
            }
            result.put(p.name(), convert(p, value));
        }
        return Collections.unmodifiableMap(result);
    }

    private static Object convert(Param p, JsonNode value) {
        String path = p.name();
        if (p instanceof StringParam s) {
            if (!value.isTextual()) {
                throw invalid(path + ": ожидается string");
            }
            String text = value.textValue();
            checkString(path, text, s.rules());
            return text;
        } else if (p instanceof IntegerParam i) {
            if (!value.isIntegralNumber() || !value.canConvertToLong()) {
                throw invalid(path + ": ожидается integer");
            }
            long n = value.longValue();
            if (i.minimum() != null && n < i.minimum()) {
                throw invalid(path + ": меньше " + i.minimum());
            }
            if (i.maximum() != null && n > i.maximum()) {
                throw invalid(path + ": больше " + i.maximum());
            }
            return n;
        } else if (p instanceof BooleanParam) {
            if (!value.isBoolean()) {
                throw invalid(path + ": ожидается boolean");
            }
            return value.booleanValue();
        } else if (p instanceof StringArrayParam a) {
            if (!value.isArray()) {
                throw invalid(path + ": ожидается array");
            }
            if (value.size() > a.maxItems()) {
                throw invalid(path + ": элементов больше " + a.maxItems());
            }
            List<String> items = new ArrayList<>(value.size());
            for (int idx = 0; idx < value.size(); idx++) {
                String itemPath = path + "[" + idx + "]";
                JsonNode item = value.get(idx);
                if (!item.isTextual()) {
                    throw invalid(itemPath + ": ожидается string");
                }
                checkString(itemPath, item.textValue(), a.items());
                items.add(item.textValue());
            }
            return List.copyOf(items);
        }
        throw new IllegalStateException("Неизвестный тип параметра " + p.getClass());
    }

    private static void checkString(String path, String s, StringRules r) {
        int length = s.codePointCount(0, s.length());
        if (r.minLength() != null && length < r.minLength()) {
            throw invalid(path + ": длина меньше " + r.minLength());
        }
        if (length > r.maxLength()) {
            throw invalid(path + ": длина больше " + r.maxLength());
        }
        if (r.pattern() != null && !Pattern.compile(r.pattern()).matcher(s).find()) {
            throw invalid(path + ": не подходит под pattern " + r.pattern());
        }
        if (!r.enumValues().isEmpty() && !r.enumValues().contains(s)) {
            throw invalid(path + ": не из списка " + r.enumValues());
        }
    }

    private static ToolException invalid(String message) {
        return new ToolException(ErrorCode.INVALID_ARGUMENTS, message);
    }
}
