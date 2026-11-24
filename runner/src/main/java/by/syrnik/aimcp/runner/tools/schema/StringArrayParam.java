package by.syrnik.aimcp.runner.tools.schema;

import by.syrnik.aimcp.runner.tools.ErrorCode;
import by.syrnik.aimcp.runner.tools.ToolException;

import java.util.Objects;

public record StringArrayParam(String name, String description, boolean required,
                               Integer maxItems, StringRules items) implements Param {
    public static final int DEFAULT_MAX_ITEMS = 100;

    public StringArrayParam(String name, String description, boolean required, Integer maxItems, StringRules items) {
        Param.checkName(name);
        this.name = name;
        Param.checkDescription(description);
        this.description = description;
        this.required = required;

        if (maxItems < 1) {
            throw new IllegalArgumentException();
        }
        this.maxItems = maxItems;

        Objects.requireNonNull(items);
        this.items = items;
    }
}
