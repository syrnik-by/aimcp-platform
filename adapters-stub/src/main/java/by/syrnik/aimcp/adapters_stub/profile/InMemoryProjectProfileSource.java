package by.syrnik.aimcp.adapters_stub.profile;

import by.syrnik.aimcp.core.profile.ProjectProfile;
import by.syrnik.aimcp.core.profile.ProjectProfileSource;

import java.util.Objects;

/** Заглушка для тестов: возвращает переданный профиль. */
public class InMemoryProjectProfileSource implements ProjectProfileSource {

    private final ProjectProfile profile;

    public InMemoryProjectProfileSource(ProjectProfile profile) {
        this.profile = Objects.requireNonNull(profile, "profile");
    }

    @Override
    public ProjectProfile load() {
        return profile;
    }
}
