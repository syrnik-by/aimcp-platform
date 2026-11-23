package by.syrnik.aimcp.core.history.records;

import java.time.Instant;
import java.util.Objects;

public record RunSummary(RunKey key, RunInfo info, Instant startedAt, Instant finishedAt, Instant receivedAt) {

    public RunSummary {
        Objects.requireNonNull(key, "runKey");
        Objects.requireNonNull(info, "runInfo");
        Objects.requireNonNull(receivedAt, "receivedAt");
    }
}
