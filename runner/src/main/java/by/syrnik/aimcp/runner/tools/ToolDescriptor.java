package by.syrnik.aimcp.runner.tools;

import by.syrnik.aimcp.runner.tools.schema.Param;
import java.util.List;

public record ToolDescriptor(String name, String description, List<Param> params,
                             boolean readOnly, boolean idempotent) {
    public ToolDescriptor(String name, String description, List<Param> params, boolean readOnly, boolean idempotent) {
        Param.checkName(name);
        this.name = name;
        Param.checkDescription(description);
        this.description = description;
        this.params = List.copyOf(params);
        this.readOnly = readOnly;
        this.idempotent = idempotent;
    }
}
