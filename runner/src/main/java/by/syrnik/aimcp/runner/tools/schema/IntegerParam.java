package by.syrnik.aimcp.runner.tools.schema;

import by.syrnik.aimcp.runner.tools.ErrorCode;
import by.syrnik.aimcp.runner.tools.ToolException;

public record IntegerParam(String name, String description, boolean required,
                           Long minimum, Long maximum) implements Param {
    public IntegerParam(String name, String description, boolean required, Long minimum, Long maximum) {
        Param.checkName(name);
        this.name = name;
        Param.checkDescription(description);
        this.description = description;
        this.required = required;

        if (minimum > maximum) {
            throw new IllegalArgumentException();
        }
        this.minimum = minimum;
        this.maximum = maximum;
    }
}
