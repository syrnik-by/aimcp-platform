package by.syrnik.aimcp.core.profile;

import java.util.List;
import java.util.Objects;

/**
 * Профиль проекта (файл {@code at-profile.yaml}, контракт в docs/project-profile.md). Необязательные блоки
 * не бывают {@code null}: нет блока — пустой блок ({@code Testit(null, null)}, пустые списки).
 */
public record ProjectProfile(String project, String repo, String frameworkVersion, Testit testit, Run run,
                             Env env, DataPolicy dataPolicy, List<String> owners) {

    public ProjectProfile {
        Checks.requireNonBlank(project, "project");
        Checks.requireNonBlank(repo, "repo");
        Objects.requireNonNull(testit, "testit");
        Objects.requireNonNull(run, "run");
        Objects.requireNonNull(env, "env");
        Objects.requireNonNull(dataPolicy, "dataPolicy");
        owners = Checks.requireNonBlankItems(owners, "owners");
    }
}
