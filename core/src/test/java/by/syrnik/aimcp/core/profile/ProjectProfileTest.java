package by.syrnik.aimcp.core.profile;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProjectProfileTest {

    private static final Run RUN = new Run(List.of("gradle", "test"), Duration.ofHours(1));

    @Test
    void runNeedsNonEmptyCommandWithNonBlankItemsAndTimeout() {
        assertThatThrownBy(() -> new Run(List.of(), Duration.ofHours(1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Run(List.of("gradle", " "), Duration.ofHours(1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Run(null, Duration.ofHours(1))).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Run(List.of("gradle"), null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void listsAreCopiedAndImmutable() {
        List<String> command = new ArrayList<>(List.of("gradle", "test"));
        List<String> mask = new ArrayList<>(List.of("a"));
        Run run = new Run(command, Duration.ofHours(1));
        DataPolicy policy = new DataPolicy(mask, List.of());

        command.add("--info");
        mask.add("b");

        assertThat(run.command()).containsExactly("gradle", "test");
        assertThat(policy.mask()).containsExactly("a");
        assertThatThrownBy(() -> run.command().add("x")).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> policy.neverSend().add("x")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void profileNeedsProjectRepoAndBlocks() {
        Testit testit = new Testit(null, null);
        Env env = new Env(true);
        DataPolicy policy = new DataPolicy(List.of(), List.of());

        assertThatThrownBy(() -> new ProjectProfile(" ", "r", null, testit, RUN, env, policy, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProjectProfile("p", null, null, testit, RUN, env, policy, List.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ProjectProfile("p", "r", null, null, RUN, env, policy, List.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ProjectProfile("p", "r", null, testit, RUN, env, policy, List.of("")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
