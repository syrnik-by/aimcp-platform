package by.syrnik.aimcp.runner.config;

import by.syrnik.aimcp.core.masking.MaskingPort;
import by.syrnik.aimcp.core.profile.ProjectProfile;
import by.syrnik.aimcp.runner.http.ResponseWriter;
import by.syrnik.aimcp.runner.http.RunnerController;
import by.syrnik.aimcp.runner.tools.ToolRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.info.ProjectInfoAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class RunnerConfigTest {

    private static final String TOKEN = "T0KEN-value-that-must-never-leak-0123456789";
    private static final String PROFILE_YAML = String.join("\n",
            "profile_version: 1",
            "project: demo",
            "repo: https://ahcode.example.invalid/group/demo.git",
            "run:",
            "  command: [gradle, test]",
            "env:",
            "  restricted: false",
            "");

    @TempDir
    Path dir;

    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class, ProjectInfoAutoConfiguration.class))
                .withConfiguration(UserConfigurations.of(RunnerConfig.class));
    }

    private String validProfile() throws IOException {
        return Files.writeString(dir.resolve("at-profile.yaml"), PROFILE_YAML).toString();
    }

    private static String allMessages(Throwable t) {
        StringBuilder sb = new StringBuilder();
        for (Throwable c = t; c != null; c = c.getCause()) {
            sb.append(c).append('\n');
        }
        return sb.toString();
    }

    @Test
    void startsWithValidTokenAndProfile() throws IOException {
        runner().withPropertyValues(
                        "aimcp.runner.token=" + TOKEN,
                        "aimcp.runner.profile=" + validProfile(),
                        "aimcp.runner.max-request-bytes=65536",
                        "aimcp.runner.max-response-bytes=1048576",
                        "aimcp.runner.masking-budget=5s")
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx).hasSingleBean(RunnerSettings.class)
                            .hasSingleBean(ProjectProfile.class)
                            .hasSingleBean(MaskingPort.class)
                            .hasSingleBean(ResponseWriter.class)
                            .hasSingleBean(ToolRegistry.class)
                            .hasSingleBean(RunnerController.class);
                    assertThat(ctx.getBean(ProjectProfile.class).project()).isEqualTo("demo");
                    assertThat(ctx.getBean(ToolRegistry.class).all()).isEmpty();
                    assertThat(ctx.getBean(BuildProperties.class).getVersion()).isNotBlank();
                });
    }

    @Test
    void settingsAreBoundAndTokenIsNotOverwritten() throws IOException {
        runner().withPropertyValues(
                        "aimcp.runner.token=" + TOKEN,
                        "aimcp.runner.profile=" + validProfile(),
                        "aimcp.runner.max-request-bytes=100",
                        "aimcp.runner.max-response-bytes=2000",
                        "aimcp.runner.masking-budget=250ms")
                .run(ctx -> {
                    RunnerSettings s = ctx.getBean(RunnerSettings.class);
                    assertThat(s.getToken()).isEqualTo(TOKEN);
                    assertThat(s.getMaxRequestBytes()).isEqualTo(100);
                    assertThat(s.getMaxResponseBytes()).isEqualTo(2000);
                    assertThat(s.getMaskingBudget()).isEqualTo(Duration.ofMillis(250));
                });
    }

    @Test
    void failsWithoutToken() throws IOException {
        runner().withPropertyValues(
                        "aimcp.runner.profile=" + validProfile(),
                        "aimcp.runner.max-request-bytes=65536",
                        "aimcp.runner.max-response-bytes=1048576",
                        "aimcp.runner.masking-budget=5s")
                .run(ctx -> {
                    assertThat(ctx).hasFailed();
                    assertThat(allMessages(ctx.getStartupFailure())).contains("AIMCP_RUNNER_TOKEN");
                });
    }

    @Test
    void failsWithShortTokenAndDoesNotRevealIt() throws IOException {
        String shortToken = "short-secret-value-31-charsxxxx";
        runner().withPropertyValues(
                        "aimcp.runner.token=" + shortToken,
                        "aimcp.runner.profile=" + validProfile(),
                        "aimcp.runner.max-request-bytes=65536",
                        "aimcp.runner.max-response-bytes=1048576",
                        "aimcp.runner.masking-budget=5s")
                .run(ctx -> {
                    assertThat(ctx).hasFailed();
                    String messages = allMessages(ctx.getStartupFailure());
                    assertThat(messages).contains("AIMCP_RUNNER_TOKEN").doesNotContain(shortToken);
                });
    }

    @Test
    void failsWhenProfileFileIsMissing() {
        runner().withPropertyValues(
                        "aimcp.runner.token=" + TOKEN,
                        "aimcp.runner.profile=" + dir.resolve("absent.yaml"),
                        "aimcp.runner.max-request-bytes=65536",
                        "aimcp.runner.max-response-bytes=1048576",
                        "aimcp.runner.masking-budget=5s")
                .run(ctx -> {
                    assertThat(ctx).hasFailed();
                    assertThat(allMessages(ctx.getStartupFailure())).doesNotContain(TOKEN);
                });
    }

    @Test
    void failsWhenProfileIsInvalid() throws IOException {
        String broken = Files.writeString(dir.resolve("broken.yaml"), "profile_version: 99\nproject: demo\n").toString();
        runner().withPropertyValues(
                        "aimcp.runner.token=" + TOKEN,
                        "aimcp.runner.profile=" + broken,
                        "aimcp.runner.max-request-bytes=65536",
                        "aimcp.runner.max-response-bytes=1048576",
                        "aimcp.runner.masking-budget=5s")
                .run(ctx -> {
                    assertThat(ctx).hasFailed();
                    assertThat(allMessages(ctx.getStartupFailure())).doesNotContain(TOKEN);
                });
    }

    @Test
    void failsWhenResponseLimitIsBelowErrorEnvelope() throws IOException {
        runner().withPropertyValues(
                        "aimcp.runner.token=" + TOKEN,
                        "aimcp.runner.profile=" + validProfile(),
                        "aimcp.runner.max-request-bytes=65536",
                        "aimcp.runner.max-response-bytes=10",
                        "aimcp.runner.masking-budget=5s")
                .run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void failsWithZeroMaskingBudget() throws IOException {
        runner().withPropertyValues(
                        "aimcp.runner.token=" + TOKEN,
                        "aimcp.runner.profile=" + validProfile(),
                        "aimcp.runner.max-request-bytes=65536",
                        "aimcp.runner.max-response-bytes=1048576",
                        "aimcp.runner.masking-budget=0s")
                .run(ctx -> assertThat(ctx).hasFailed());
    }
}
