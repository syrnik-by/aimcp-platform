package by.syrnik.aimcp.runner.tools.schema;

public record IntegerParam(String name, String description, boolean required,
                           Long minimum, Long maximum) implements Param {
    public IntegerParam{
        Param.checkName(name);
        Param.checkDescription(description);
        if (minimum != null && maximum != null && minimum > maximum) {
            throw new IllegalArgumentException();
        }
    }
}
