package by.syrnik.aimcp.runner.tools;

import java.util.*;

public final class ToolRegistry {
    private final Map<String, Tool> byName;

    public ToolRegistry(List<Tool> tools) {
        byName = new LinkedHashMap<>();
        for (Tool tool : tools) {
            if (!byName.containsKey(tool.descriptor().name())) {
                byName.put(tool.descriptor().name(), tool);
            } else {
                throw new IllegalArgumentException("дублируется инструмент " + tool.descriptor().name());
            }
        }
    }

    public Optional<Tool> find(String name) {
        return Optional.ofNullable(byName.get(name));
    }

    public Collection<Tool> all() {
        return Collections.unmodifiableCollection(byName.values());
    }
}
