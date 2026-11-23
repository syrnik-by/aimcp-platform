package by.syrnik.aimcp.techzone.ingest;

import by.syrnik.aimcp.adapters_stub.history.InMemoryRunHistory;
import by.syrnik.aimcp.core.history.RunHistoryPort;
import by.syrnik.aimcp.core.history.records.RunInfo;
import by.syrnik.aimcp.core.history.records.RunKey;
import by.syrnik.aimcp.core.history.records.TestResult;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ResultIngestServiceTest {

    private static final String PROJECT = "demo";

    private final RunHistoryPort history = new InMemoryRunHistory(Clock.systemUTC());
    private final ResultIngestService service = new ResultIngestService(history, new ResultFileParser());

    @Test
    void emptyBatchIsAccepted() {
        assertThat(service.ingest(PROJECT, List.of())).isEqualTo(new IngestReport(0, 0, 0));
    }

    @Test
    void everyResultOfTheBatchIsSaved() {
        IngestReport report = service.ingest(PROJECT, List.of(
                ResultDocuments.of("run-1", "u-1"),
                ResultDocuments.of("run-1", "u-2"),
                ResultDocuments.of("run-1", "u-3")));

        assertThat(report).isEqualTo(new IngestReport(3, 3, 0));
        assertThat(history.findResults(new RunKey(PROJECT, "run-1")))
                .extracting(TestResult::uuid).containsExactlyInAnyOrder("u-1", "u-2", "u-3");
    }

    @Test
    void repeatedBatchAddsNothing() {
        List<ObjectNode> batch = List.of(ResultDocuments.of("run-1", "u-1"), ResultDocuments.of("run-1", "u-2"));
        service.ingest(PROJECT, List.copyOf(batch));

        IngestReport report = service.ingest(PROJECT, List.copyOf(batch));

        assertThat(report).isEqualTo(new IngestReport(2, 0, 2));
        assertThat(history.findResults(new RunKey(PROJECT, "run-1"))).hasSize(2);
    }

    @Test
    void onlyNewResultsAreCountedWhenBatchOverlapsWithSavedOnes() {
        service.ingest(PROJECT, List.of(ResultDocuments.of("run-1", "u-1")));

        IngestReport report = service.ingest(PROJECT, List.of(
                ResultDocuments.of("run-1", "u-1"),
                ResultDocuments.of("run-1", "u-2")));

        assertThat(report).isEqualTo(new IngestReport(2, 1, 1));
    }

    @Test
    void sameUuidTwiceInOneBatchIsStoredOnce() {
        IngestReport report = service.ingest(PROJECT, List.of(
                ResultDocuments.of("run-1", "u-1"),
                ResultDocuments.of("run-1", "u-1")));

        assertThat(report).isEqualTo(new IngestReport(2, 1, 1));
    }

    @Test
    void resultsGoToTheRunsNamedInTheirDocuments() {
        IngestReport report = service.ingest(PROJECT, List.of(
                ResultDocuments.of("run-a", "u-1"),
                ResultDocuments.of("run-b", "u-2"),
                ResultDocuments.of("run-a", "u-3")));

        assertThat(report).isEqualTo(new IngestReport(3, 3, 0));
        assertThat(history.findResults(new RunKey(PROJECT, "run-a"))).extracting(TestResult::uuid)
                .containsExactlyInAnyOrder("u-1", "u-3");
        assertThat(history.findResults(new RunKey(PROJECT, "run-b"))).extracting(TestResult::uuid)
                .containsExactly("u-2");
    }

    @Test
    void sameRunIdInAnotherProjectIsAnotherRun() {
        service.ingest("project-a", List.of(ResultDocuments.of("run-1", "u-1")));
        service.ingest("project-b", List.of(ResultDocuments.of("run-1", "u-2")));

        assertThat(history.findResults(new RunKey("project-a", "run-1"))).extracting(TestResult::uuid)
                .containsExactly("u-1");
        assertThat(history.findResults(new RunKey("project-b", "run-1"))).extracting(TestResult::uuid)
                .containsExactly("u-2");
    }

    @Test
    void partsOfOneRunWithDifferentCiInfoFillGapsInTheRun() {
        ObjectNode withoutCi = ResultDocuments.of("run-1", "u-1");
        ((ObjectNode) withoutCi.get("run")).remove("ci");
        ObjectNode withCi = ResultDocuments.of("run-1", "u-2");

        service.ingest(PROJECT, List.of(withoutCi, withCi));

        RunInfo info = history.findRun(new RunKey(PROJECT, "run-1")).orElseThrow().info();
        assertThat(info.pipelineId()).isEqualTo("310045");
        assertThat(info.projectPath()).isEqualTo("group/project-tests");
        assertThat(history.findResults(new RunKey(PROJECT, "run-1"))).hasSize(2);
    }

    @Test
    void documentWithUnknownSchemaVersionIsSkipped() {
        ObjectNode future = ResultDocuments.of("run-1", "u-2");
        future.put("schemaVersion", 2);

        IngestReport report = service.ingest(PROJECT, List.of(ResultDocuments.of("run-1", "u-1"), future));

        assertThat(report).isEqualTo(new IngestReport(2, 1, 1));
        assertThat(history.findResults(new RunKey(PROJECT, "run-1"))).extracting(TestResult::uuid)
                .containsExactly("u-1");
    }

    @Test
    void corruptedDocumentRejectsTheWholeBatchAndNothingIsSaved() {
        ObjectNode broken = ResultDocuments.of("run-1", "u-2");
        ((ObjectNode) broken.get("test")).remove("name");

        assertThatThrownBy(() -> service.ingest(PROJECT, List.of(
                ResultDocuments.of("run-1", "u-1"), broken, ResultDocuments.of("run-1", "u-3"))))
                .isInstanceOf(InvalidResultFileException.class)
                .hasMessageStartingWith("документ N2: /test/name:");

        assertThat(history.findRun(new RunKey(PROJECT, "run-1"))).isEmpty();
        assertThat(history.findResults(new RunKey(PROJECT, "run-1"))).isEmpty();
    }

    @Test
    void storageFailureIsNotReportedAsInvalidFile() {
        RunHistoryPort failing = mock(RunHistoryPort.class);
        when(failing.save(any(), any(), any())).thenThrow(new IllegalStateException("хранилище недоступно"));
        ResultIngestService serviceWithFailingStorage = new ResultIngestService(failing, new ResultFileParser());

        assertThatThrownBy(() -> serviceWithFailingStorage.ingest(PROJECT, List.of(ResultDocuments.of("run-1", "u-1"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("хранилище недоступно");
    }
}
