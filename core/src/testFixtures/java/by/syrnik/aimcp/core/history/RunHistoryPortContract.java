package by.syrnik.aimcp.core.history;

import by.syrnik.aimcp.core.history.enums.FailureKind;
import by.syrnik.aimcp.core.history.enums.TestStatus;
import by.syrnik.aimcp.core.history.records.Failure;
import by.syrnik.aimcp.core.history.records.Link;
import by.syrnik.aimcp.core.history.records.PartInfo;
import by.syrnik.aimcp.core.history.records.RunInfo;
import by.syrnik.aimcp.core.history.records.RunKey;
import by.syrnik.aimcp.core.history.records.RunSummary;
import by.syrnik.aimcp.core.history.records.Step;
import by.syrnik.aimcp.core.history.records.Suppressed;
import by.syrnik.aimcp.core.history.records.TestResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Общие проверки правил {@link RunHistoryPort}: наследуются тестом заглушки. Тесты проекта не используют БД и
 * контейнеры (D-028), поэтому JDBC-реализацию этот набор в сборке не проходит: её проверяют вручную на живой БД,
 * унаследовав этот класс вне проекта и вернув {@code JdbcRunHistory} из {@link #createPort()}.
 * Время в данных — с точностью до миллисекунды: шаги в хранилище хранят миллисекунды.
 */
public abstract class RunHistoryPortContract {

    private static final Instant T0 = Instant.parse("2026-03-01T10:00:00Z");

    protected RunHistoryPort port;

    /** Новый пустой экземпляр порта; ключи прогонов в тестах уникальны, очищать хранилище не нужно. */
    protected abstract RunHistoryPort createPort();

    @BeforeEach
    void createPortForTest() {
        port = createPort();
    }

    @Test
    void unknownRunHasNoSummaryAndNoResults() {
        RunKey key = newKey();

        assertThat(port.findRun(key)).isEmpty();
        assertThat(port.findResults(key)).isEmpty();
    }

    @Test
    void savedResultIsReadBackWithAllFields() {
        RunKey key = newKey();
        Step step = new Step("открыть страницу", "описание", TestStatus.FAILED, T0.plusMillis(10), T0.plusMillis(20),
                Map.of("url", "http://stand"), List.of("/tmp/step.png"),
                new Failure(FailureKind.ASSERTION, "java.lang.AssertionError", "не совпало", "trace", List.of()),
                List.of(new Step("вложенный", null, TestStatus.PASSED, null, null, Map.of(), List.of(), null, List.of())));
        Failure failure = new Failure(FailureKind.ERROR, "java.lang.IllegalStateException", null, "at Foo.bar",
                List.of(new Suppressed("java.io.IOException", "причина")));
        TestResult full = new TestResult(part("job-1"), 1, "u-1", "ext-1", List.of("WI-1", "WI-2"), "ns", "Cls",
                "name", "title", "description", "message", TestStatus.FAILED, T0, T0.plusSeconds(3),
                List.of("smoke", "ui"), List.of(new Link("Issue", "баг", "http://tracker/1")),
                Map.of("browser", "chrome"), List.of(step), List.of("/tmp/a.png"), failure);
        TestResult minimal = result("u-2", "minimal", null, null);

        port.save(key, info("1", "abc", "main", "grp/prj", "7"), List.of(full, minimal));

        assertThat(port.findResults(key)).containsExactly(full, minimal);
    }

    @Test
    void saveReturnsNumberOfResultsAddedForTheFirstTime() {
        RunKey key = newKey();

        int added = port.save(key, emptyInfo(), List.of(result("a", "a", T0, T0), result("b", "b", T0, T0)));

        assertThat(added).isEqualTo(2);
    }

    @Test
    void laterPartsExtendTheRunInsteadOfReplacingIt() {
        RunKey key = newKey();

        port.save(key, emptyInfo(), List.of(result("a", "a", T0, T0), result("b", "b", T0, T0)));
        int added = port.save(key, emptyInfo(), List.of(result("c", "c", T0, T0)));

        assertThat(added).isEqualTo(1);
        assertThat(port.findResults(key)).extracting(TestResult::uuid).containsExactlyInAnyOrder("a", "b", "c");
    }

    @Test
    void repeatedSaveOfTheSameResultsAddsNothing() {
        RunKey key = newKey();
        List<TestResult> results = List.of(result("a", "a", T0, T0), result("b", "b", T0, T0));
        port.save(key, emptyInfo(), results);

        int added = port.save(key, emptyInfo(), results);

        assertThat(added).isZero();
        assertThat(port.findResults(key)).hasSize(2);
    }

    @Test
    void resultWithKnownUuidIsSkippedAndFirstVersionStays() {
        RunKey key = newKey();
        port.save(key, emptyInfo(), List.of(result("a", "первая версия", T0, T0)));

        int added = port.save(key, emptyInfo(), List.of(result("a", "вторая версия", T0, T0)));

        assertThat(added).isZero();
        assertThat(port.findResults(key)).extracting(TestResult::name).containsExactly("первая версия");
    }

    @Test
    void sameUuidInOneCallIsStoredOnce() {
        RunKey key = newKey();

        int added = port.save(key, emptyInfo(),
                List.of(result("a", "первая версия", T0, T0), result("a", "вторая версия", T0, T0)));

        assertThat(added).isEqualTo(1);
        assertThat(port.findResults(key)).extracting(TestResult::name).containsExactly("первая версия");
    }

    @Test
    void runInfoKeepsFirstNonBlankValueAndFillsGapsFromLaterParts() {
        RunKey key = newKey();
        port.save(key, info("100", null, "", "grp/prj", null), List.of(result("a", "a", T0, T0)));
        port.save(key, info("200", "abc", "main", "other/prj", "7"), List.of(result("b", "b", T0, T0)));

        RunSummary summary = port.findRun(key).orElseThrow();

        assertThat(summary.key()).isEqualTo(key);
        assertThat(summary.info()).isEqualTo(info("100", "abc", "main", "grp/prj", "7"));
    }

    @Test
    void summaryCoversEarliestStartAndLatestFinishOfAllParts() {
        RunKey key = newKey();
        port.save(key, emptyInfo(), List.of(result("a", "a", T0.plusSeconds(60), T0.plusSeconds(90))));
        port.save(key, emptyInfo(), List.of(
                result("b", "b", T0, T0.plusSeconds(30)),
                result("c", "c", T0.plusSeconds(100), T0.plusSeconds(200)),
                result("d", "d", T0.plusSeconds(10), T0.plusSeconds(40))));

        RunSummary summary = port.findRun(key).orElseThrow();

        assertThat(summary.startedAt()).isEqualTo(T0);
        assertThat(summary.finishedAt()).isEqualTo(T0.plusSeconds(200));
    }

    @Test
    void resultsWithoutTimesDoNotAffectSummaryBounds() {
        RunKey key = newKey();
        port.save(key, emptyInfo(), List.of(
                result("a", "a", null, null),
                result("b", "b", T0, T0.plusSeconds(5))));

        RunSummary summary = port.findRun(key).orElseThrow();

        assertThat(summary.startedAt()).isEqualTo(T0);
        assertThat(summary.finishedAt()).isEqualTo(T0.plusSeconds(5));
    }

    @Test
    void summaryTimesAreNullWhenNoResultHasTimes() {
        RunKey key = newKey();
        port.save(key, emptyInfo(), List.of(result("a", "a", null, null)));

        RunSummary summary = port.findRun(key).orElseThrow();

        assertThat(summary.startedAt()).isNull();
        assertThat(summary.finishedAt()).isNull();
    }

    @Test
    void receivedAtIsSetOnFirstSaveAndNotChangedByLaterOnes() throws InterruptedException {
        RunKey key = newKey();
        port.save(key, emptyInfo(), List.of(result("a", "a", T0, T0)));
        Instant first = port.findRun(key).orElseThrow().receivedAt();
        Thread.sleep(20);

        port.save(key, emptyInfo(), List.of(result("b", "b", T0, T0)));

        assertThat(first).isNotNull();
        assertThat(port.findRun(key).orElseThrow().receivedAt()).isEqualTo(first);
    }

    @Test
    void sameRunIdInDifferentProjectsIsDifferentRuns() {
        String runId = UUID.randomUUID().toString();
        RunKey first = new RunKey("project-one", runId);
        RunKey second = new RunKey("project-two", runId);

        port.save(first, info("1", null, null, null, null), List.of(result("a", "a", T0, T0)));
        port.save(second, info("2", null, null, null, null), List.of(result("a", "a", T0, T0), result("b", "b", T0, T0)));

        assertThat(port.findResults(first)).hasSize(1);
        assertThat(port.findResults(second)).hasSize(2);
        assertThat(port.findRun(first).orElseThrow().info().pipelineId()).isEqualTo("1");
        assertThat(port.findRun(second).orElseThrow().info().pipelineId()).isEqualTo("2");
    }

    @Test
    void resultsAreOrderedByStartThenUuidWithUnknownStartLast() {
        RunKey key = newKey();
        port.save(key, emptyInfo(), List.of(
                result("e", "e", null, null),
                result("c", "c", T0.plusSeconds(5), null),
                result("b", "b", T0, null),
                result("d", "d", null, null),
                result("a", "a", T0, null)));

        assertThat(port.findResults(key)).extracting(TestResult::uuid).containsExactly("a", "b", "c", "d", "e");
    }

    @Test
    void concurrentSavesOfOneRunNeitherLoseNorDuplicateResults() throws Exception {
        RunKey key = newKey();
        int threads = 4;
        int ownPerThread = 10;
        int shared = 5;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> added = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            List<TestResult> part = new ArrayList<>();
            for (int i = 0; i < ownPerThread; i++) {
                part.add(result("own-" + t + "-" + i, "own", T0, T0));
            }
            for (int i = 0; i < shared; i++) {
                part.add(result("shared-" + i, "shared", T0, T0));
            }
            added.add(pool.submit(() -> {
                start.await();
                return port.save(key, emptyInfo(), part);
            }));
        }
        start.countDown();
        int total = 0;
        for (Future<Integer> f : added) {
            total += f.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertThat(total).isEqualTo(threads * ownPerThread + shared);
        assertThat(port.findResults(key)).hasSize(threads * ownPerThread + shared);
    }

    protected static RunKey newKey() {
        return new RunKey("contract-project", UUID.randomUUID().toString());
    }

    protected static RunInfo emptyInfo() {
        return info(null, null, null, null, null);
    }

    protected static RunInfo info(String pipelineId, String commitSha, String ref, String projectPath, String mergeRequestIid) {
        return new RunInfo(pipelineId, commitSha, ref, projectPath, mergeRequestIid);
    }

    protected static PartInfo part(String jobId) {
        return new PartInfo(jobId, "http://ci/jobs/" + jobId, "regression 1/4", "vm-01", "1.2.3", "tms-1");
    }

    protected static TestResult result(String uuid, String name, Instant startedAt, Instant stoppedAt) {
        return new TestResult(part("job-1"), 1, uuid, "ext-" + uuid, List.of(), null, null, name, null, null, null,
                TestStatus.PASSED, startedAt, stoppedAt, List.of(), List.of(), Map.of(), List.of(), List.of(), null);
    }
}
