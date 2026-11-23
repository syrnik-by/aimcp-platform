package by.syrnik.aimcp.adapters_stub.history;

import by.syrnik.aimcp.core.history.RunHistoryPort;
import by.syrnik.aimcp.core.history.records.RunInfo;
import by.syrnik.aimcp.core.history.records.RunKey;
import by.syrnik.aimcp.core.history.records.RunSummary;
import by.syrnik.aimcp.core.history.records.TestResult;

import java.time.Clock;
import java.time.Instant;
import java.util.*;

public class InMemoryRunHistory implements RunHistoryPort {

    private final Clock clock;
    private final Map<RunKey, Entry> runs = new HashMap<>();

    public InMemoryRunHistory(Clock clock) {
        this.clock = clock;
    }

    private static final class Entry {
        final Instant receivedAt;
        RunInfo info = new RunInfo(null, null, null, null, null);
        final Map<String, TestResult> results = new LinkedHashMap<>();

        Entry(Instant receivedAt) {
            this.receivedAt = receivedAt;
        }
    }

    @Override
    public synchronized int save(RunKey key, RunInfo info, List<TestResult> results) {
        Entry entry = runs.computeIfAbsent(key, k -> new Entry(clock.instant()));
        entry.info = merge(entry.info, info);
        int addedCount = 0;

        for (TestResult r : results) {
            if (entry.results.putIfAbsent(r.uuid(), r) == null) {
                addedCount++;
            }
        }
        return addedCount;
    }

    @Override
    public synchronized Optional<RunSummary> findRun(RunKey key) {
        if (runs.get(key) != null) {
            Entry entry = runs.get(key);
            Instant startedAt = entry.results.values().stream().map(TestResult::startedAt).filter(Objects::nonNull).min(Comparator.naturalOrder()).orElse(null);
            Instant stoppedAt = entry.results.values().stream().map(TestResult::stoppedAt).filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);
            return Optional.of(new RunSummary(key, entry.info, startedAt, stoppedAt, entry.receivedAt));
        } else {
            return Optional.empty();
        }
    }

    @Override
    public synchronized List<TestResult> findResults(RunKey key) {
        if (runs.get(key) != null) {
            Entry entry = runs.get(key);
            return entry.results.values().stream().sorted(Comparator.comparing(TestResult::startedAt, Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(TestResult::uuid)).toList();
        } else {
            return List.of();
        }
    }

    private static String keepFirst(String current, String incoming) {
        return current != null && !current.isBlank() ? current : incoming;
    }

    private static RunInfo merge(RunInfo old, RunInfo in) {
        return new RunInfo(
                keepFirst(old.pipelineId(), in.pipelineId()),
                keepFirst(old.commitSha(), in.commitSha()),
                keepFirst(old.ref(), in.ref()),
                keepFirst(old.projectPath(), in.projectPath()),
                keepFirst(old.mergeRequestIid(), in.mergeRequestIid()));
    }
}
