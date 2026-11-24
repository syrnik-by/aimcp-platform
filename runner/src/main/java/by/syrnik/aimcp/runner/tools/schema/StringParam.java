package by.syrnik.aimcp.runner.tools.schema;

import java.util.Objects;

public record StringParam(String name, String description, boolean required,
                          StringRules rules) implements Param {

    public StringParam{
        Param.checkName(name);
        Param.checkDescription(description);
        Objects.requireNonNull(rules);
    }
}
