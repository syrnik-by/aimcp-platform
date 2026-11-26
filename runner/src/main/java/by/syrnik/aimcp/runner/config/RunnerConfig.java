package by.syrnik.aimcp.runner.config;

import by.syrnik.aimcp.core.masking.MaskingPort;
import by.syrnik.aimcp.core.profile.ProjectProfile;
import by.syrnik.aimcp.runner.http.ResponseWriter;
import by.syrnik.aimcp.runner.http.RunnerController;
import by.syrnik.aimcp.runner.masking.RegexMaskingPort;
import by.syrnik.aimcp.runner.profile.YamlProjectProfileSource;
import by.syrnik.aimcp.runner.tools.Tool;
import by.syrnik.aimcp.runner.tools.ToolRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.util.stream.Collectors;

@Configuration
@EnableConfigurationProperties(RunnerSettings.class)
public class RunnerConfig {

    @Bean
    public ProjectProfile projectProfile(RunnerSettings settings) {
        return new YamlProjectProfileSource(Path.of(settings.getProfile())).load();
    }

    @Bean
    public MaskingPort maskingPort(ProjectProfile projectProfile, RunnerSettings settings) {
        return RegexMaskingPort.forPolicy(projectProfile.dataPolicy(), settings.getMaskingBudget());
    }

    @Bean
    public ResponseWriter responseWriter(ObjectMapper objectMapper, RunnerSettings settings, MaskingPort maskingPort) {
        return new ResponseWriter(objectMapper, settings.getMaxResponseBytes(), maskingPort);
    }

    @Bean
    public ToolRegistry toolRegistry(ObjectProvider<Tool> tools) {
        return new ToolRegistry(tools.orderedStream().collect(Collectors.toList()));
    }

    @Bean
    RunnerController runnerController(ToolRegistry toolRegistry, ObjectMapper objectMapper, MaskingPort maskingPort,
                                      ResponseWriter responseWriter, ProjectProfile profile, BuildProperties build, RunnerSettings settings) {
        return new RunnerController(toolRegistry, objectMapper, maskingPort, responseWriter,
                profile.project(), build.getVersion(), settings.getMaxRequestBytes());
    }
}
