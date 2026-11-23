package by.syrnik.aimcp.core.history.records;

public record RunKey(
        String project,
        String runId
) {
    public RunKey {
        Checks.requireNonBlank(project, "project");
        Checks.requireNonBlank(runId, "runId");
    }

}
