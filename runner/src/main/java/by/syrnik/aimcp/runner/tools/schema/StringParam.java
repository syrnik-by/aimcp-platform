package by.syrnik.aimcp.runner.tools.schema;

public record StringParam(String name, String description, boolean required,
                          StringRules rules) implements Param {

    public StringParam(String name, String description, boolean required, StringRules rules) {
        Param.checkName(name);
        this.name = name;
        Param.checkDescription(description);
        this.description = description;
        this.required = required;
        this.rules = rules;
    }
}
