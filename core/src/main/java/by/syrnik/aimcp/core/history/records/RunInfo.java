package by.syrnik.aimcp.core.history.records;

public record RunInfo(String pipelineId, String commitSha, String ref, String projectPath, String mergeRequestIid) {
}
