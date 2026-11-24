package by.syrnik.aimcp.runner.tools.schema;

public record BooleanParam(String name, String description, boolean required)
        implements Param {
    public BooleanParam{
        Param.checkName(name);
        Param.checkDescription(description);
    }
}
