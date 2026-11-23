package by.syrnik.aimcp.techzone.ingest;

import by.syrnik.aimcp.core.history.records.RunInfo;
import by.syrnik.aimcp.core.history.records.TestResult;

public record ParsedResult(String runId, RunInfo runInfo, TestResult testResult) {
}
