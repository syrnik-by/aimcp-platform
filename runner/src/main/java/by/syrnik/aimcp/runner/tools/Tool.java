package by.syrnik.aimcp.runner.tools;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Map;

public interface Tool {
    ToolDescriptor descriptor();
    ObjectNode call(Map<String, Object> args);
}
