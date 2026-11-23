package by.syrnik.aimcp.adapters_stub.profile;

import by.syrnik.aimcp.core.profile.DataPolicy;
import by.syrnik.aimcp.core.profile.Env;
import by.syrnik.aimcp.core.profile.ProjectProfile;
import by.syrnik.aimcp.core.profile.Run;
import by.syrnik.aimcp.core.profile.Testit;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryProjectProfileSourceTest {

    @Test
    void loadReturnsTheGivenProfile() {
        ProjectProfile profile = new ProjectProfile("demo", "https://ahcode.example.invalid/demo.git", null,
                new Testit(null, null), new Run(List.of("gradle", "test"), Duration.ofHours(1)), new Env(false),
                new DataPolicy(List.of(), List.of()), List.of());

        assertThat(new InMemoryProjectProfileSource(profile).load()).isSameAs(profile);
    }

    @Test
    void nullProfileIsRejected() {
        assertThatThrownBy(() -> new InMemoryProjectProfileSource(null)).isInstanceOf(NullPointerException.class);
    }
}
