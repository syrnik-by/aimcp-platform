package by.syrnik.aimcp.runner.http;

import by.syrnik.aimcp.runner.tools.Tool;
import by.syrnik.aimcp.runner.tools.ToolDescriptor;
import by.syrnik.aimcp.runner.tools.ToolRegistry;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolRegistryTest {

    private static Tool tool(String name) {
        ToolDescriptor descriptor = new ToolDescriptor(name, "d", List.of(), true, true);
        return new Tool() {
            @Override
            public ToolDescriptor descriptor() {
                return descriptor;
            }

            @Override
            public ObjectNode call(Map<String, Object> args) {
                return JsonNodeFactory.instance.objectNode();
            }
        };
    }

    @Test
    void findsToolByName() {
        Tool a = tool("check_env");
        Tool b = tool("get_profile");

        ToolRegistry registry = new ToolRegistry(List.of(a, b));

        assertThat(registry.find("check_env")).containsSame(a);
        assertThat(registry.find("get_profile")).containsSame(b);
    }

    @Test
    void unknownNameIsEmpty() {
        ToolRegistry registry = new ToolRegistry(List.of(tool("check_env")));

        assertThat(registry.find("run_tests")).isEmpty();
        assertThat(registry.find("CHECK_ENV")).isEmpty();
        assertThat(registry.find("")).isEmpty();
    }

    @Test
    void keepsRegistrationOrder() {
        List<String> names = List.of("run_tests", "check_env", "get_profile", "collect_artifacts");
        List<Tool> tools = new ArrayList<>();
        names.forEach(n -> tools.add(tool(n)));

        ToolRegistry registry = new ToolRegistry(tools);

        assertThat(registry.all()).extracting(t -> t.descriptor().name()).containsExactlyElementsOf(names);
    }

    @Test
    void emptyRegistryIsAllowed() {
        ToolRegistry registry = new ToolRegistry(List.of());

        assertThat(registry.all()).isEmpty();
        assertThat(registry.find("check_env")).isEmpty();
    }

    @Test
    void duplicateNameIsRejectedWithName() {
        List<Tool> tools = List.of(tool("check_env"), tool("get_profile"), tool("check_env"));

        assertThatThrownBy(() -> new ToolRegistry(tools))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("check_env");
    }

    @Test
    void allIsUnmodifiable() {
        ToolRegistry registry = new ToolRegistry(List.of(tool("check_env")));

        assertThatThrownBy(() -> registry.all().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThat(registry.all()).hasSize(1);
    }

    @Test
    void laterChangesToSourceListDoNotAffectRegistry() {
        List<Tool> tools = new ArrayList<>(List.of(tool("check_env")));
        ToolRegistry registry = new ToolRegistry(tools);

        tools.add(tool("get_profile"));

        assertThat(registry.all()).hasSize(1);
        assertThat(registry.find("get_profile")).isEmpty();
    }
}
