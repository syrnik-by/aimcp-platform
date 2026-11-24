package by.syrnik.aimcp.runner.tools.schema;

public record BooleanParam(String name, String description, boolean required)
        implements Param {
    public BooleanParam(String name, String description, boolean required) {
        Param.checkName(name);
        this.name = name;
        Param.checkDescription(description);
        this.description = description;
        this.required = required;
    }
}
