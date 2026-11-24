package by.syrnik.aimcp.runner.tools.schema;

import java.util.Objects;

public record StringArrayParam(String name, String description, boolean required,
                               Integer maxItems, StringRules items) implements Param {
    public static final int DEFAULT_MAX_ITEMS = 100;

    public StringArrayParam{
        Param.checkName(name);
        Param.checkDescription(description);
        maxItems = maxItems != null ? maxItems : DEFAULT_MAX_ITEMS;
        if (maxItems < 1) {
            throw new IllegalArgumentException("Проверьте значение поля maxItems параметра " + name);
        }
        Objects.requireNonNull(items);
    }
}
