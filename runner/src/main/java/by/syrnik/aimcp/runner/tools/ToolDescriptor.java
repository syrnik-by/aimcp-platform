package by.syrnik.aimcp.runner.tools;

import by.syrnik.aimcp.runner.tools.schema.Param;

import java.util.HashSet;
import java.util.List;

public record ToolDescriptor(String name, String description, List<Param> params,
                             boolean readOnly, boolean idempotent) {
    public ToolDescriptor {
        Param.checkName(name);
        Param.checkDescription(description);
        params = List.copyOf(params);
        HashSet<String> paramsNames = new HashSet<>();
        for (Param p : params) {
            if (!paramsNames.add(p.name())) {
                throw new IllegalArgumentException(String.valueOf(p));
            }
        }
    }
}
