package by.syrnik.aimcp.adapters_postgres.history;

import by.syrnik.aimcp.core.history.RunHistoryPort;
import by.syrnik.aimcp.core.history.enums.FailureKind;
import by.syrnik.aimcp.core.history.enums.TestStatus;
import by.syrnik.aimcp.core.history.records.*;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Array;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class JdbcRunHistory implements RunHistoryPort {

    private final JdbcTemplate template;
    private final TransactionTemplate transactionTemplate;

    private static final String RUN_SQL = "INSERT INTO run (project, run_id, ci_pipeline_id, ci_commit_sha, ci_ref, ci_project_path, ci_merge_request_iid) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?) " +
            "ON CONFLICT (project, run_id) DO UPDATE SET " +
            "    ci_pipeline_id = CASE WHEN btrim(run.ci_pipeline_id) <> '' THEN run.ci_pipeline_id ELSE EXCLUDED.ci_pipeline_id END, " +
            "    ci_commit_sha = CASE WHEN btrim(run.ci_commit_sha) <> '' THEN run.ci_commit_sha ELSE EXCLUDED.ci_commit_sha END, " +
            "    ci_ref = CASE WHEN btrim(run.ci_ref) <> '' THEN run.ci_ref ELSE EXCLUDED.ci_ref END, " +
            "    ci_project_path = CASE WHEN btrim(run.ci_project_path) <> '' THEN run.ci_project_path ELSE EXCLUDED.ci_project_path END, " +
            "    ci_merge_request_iid = CASE WHEN btrim(run.ci_merge_request_iid) <> '' THEN run.ci_merge_request_iid ELSE EXCLUDED.ci_merge_request_iid END " +
            "RETURNING id";

    private static final String TEST_RESULT_SQL =
            "INSERT INTO test_result (" +
                    "    run_pk, tms_run_id, adapter_version, host, ci_job_id, ci_job_url, ci_job_name, " +
                    "    schema_version, uuid, external_id, work_item_ids, namespace, class_name, " +
                    "    name, title, description, message, status, started_at, stopped_at, " +
                    "    labels, links, parameters, steps, attachments, " +
                    "    failure_kind, failure_type, failure_message, failure_trace, failure_suppressed" +
                    ") VALUES (" +
                    "    ?, ?, ?, ?, ?, ?, ?, " +
                    "    ?, ?, ?, ?, ?, ?, " +
                    "    ?, ?, ?, ?, ?, ?, ?, " +
                    "    ?, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, " +
                    "    ?, ?, ?, ?, ?::jsonb" +
                    ") ON CONFLICT (run_pk, uuid) DO NOTHING " +
                    "RETURNING id";

    private static final String RUN_BOUNDS_SQL =
            "UPDATE run SET " +
                    "started_at = (SELECT min(started_at) FROM test_result WHERE run_pk = run.id), " +
                    "finished_at = (SELECT max(stopped_at) FROM test_result WHERE run_pk = run.id) " +
                    "WHERE id = ?";

    private static final String GET_TEST_RESULT_SQL =
            "SELECT t.* FROM test_result t JOIN run r ON r.id = t.run_pk " +
                    "WHERE r.run_id = ? AND r.project = ? " +
                    "ORDER BY t.started_at ASC NULLS LAST, t.uuid COLLATE \"C\"";

    private static final String GET_RUN_SQL =
            "SELECT * FROM run WHERE run_id = ? AND project = ?";

    public JdbcRunHistory(DataSource dataSource) {
        this.template = new JdbcTemplate(dataSource);
        DataSourceTransactionManager txManager = new DataSourceTransactionManager(dataSource);
        this.transactionTemplate = new TransactionTemplate(txManager);
    }

    @Override
    public int save(RunKey key, RunInfo info, List<TestResult> results) {
        return Objects.requireNonNull(transactionTemplate.execute(status -> {

            long runPk = Objects.requireNonNull(template.queryForObject(
                    RUN_SQL,
                    Long.class,
                    key.project(),
                    key.runId(),
                    info.pipelineId(),
                    info.commitSha(),
                    info.ref(),
                    info.projectPath(),
                    info.mergeRequestIid()
            ));

            return template.execute((Connection conn) -> {
                Integer insertedCount = 0;
                try (java.sql.PreparedStatement ps = conn.prepareStatement(TEST_RESULT_SQL)) {
                    for (TestResult testResult : results) {
                        int p = 1;

                        ps.setLong(p++, runPk);
                        ps.setString(p++, testResult.part().tmsRunId());
                        ps.setString(p++, testResult.part().adapterVersion());
                        ps.setString(p++, testResult.part().host());
                        ps.setString(p++, testResult.part().jobId());
                        ps.setString(p++, testResult.part().jobUrl());
                        ps.setString(p++, testResult.part().jobName());

                        ps.setShort(p++, (short) testResult.schemaVersion());
                        ps.setString(p++, testResult.uuid());
                        ps.setString(p++, testResult.externalId());

                        Array workItemIdsArray = conn.createArrayOf("text", testResult.workItemIds().toArray());
                        ps.setArray(p++, workItemIdsArray);

                        ps.setString(p++, testResult.namespace());
                        ps.setString(p++, testResult.className());
                        ps.setString(p++, testResult.name());
                        ps.setString(p++, testResult.title());
                        ps.setString(p++, testResult.description());
                        ps.setString(p++, testResult.message());
                        ps.setString(p++, testResult.status().name());

                        ps.setTimestamp(p++, testResult.startedAt() != null ? Timestamp.from(testResult.startedAt()) : null);
                        ps.setTimestamp(p++, testResult.stoppedAt() != null ? Timestamp.from(testResult.stoppedAt()) : null);

                        Array labelsArray = conn.createArrayOf("text", testResult.labels().toArray());
                        ps.setArray(p++, labelsArray);

                        ps.setString(p++, JsonUtils.toJson(testResult.links(), "[]"));
                        ps.setString(p++, JsonUtils.toJson(testResult.parameters(), "{}"));
                        ps.setString(p++, JsonUtils.stepsToJson(testResult.steps()));
                        ps.setString(p++, JsonUtils.attachmentsToJson(testResult.attachments()));

                        Failure failure = testResult.failure();
                        ps.setString(p++, Objects.isNull(failure) ? null : failure.kind().name());
                        ps.setString(p++, Objects.isNull(failure) ? null : failure.type());
                        ps.setString(p++, Objects.isNull(failure) ? null : failure.message());
                        ps.setString(p++, Objects.isNull(failure) ? null : failure.trace());
                        ps.setString(p++, Objects.isNull(failure) ? "[]" : JsonUtils.toJson(failure.suppressed(), "[]"));

                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) insertedCount++;
                        }
                    }

                }
                template.update(RUN_BOUNDS_SQL, runPk);
                return Objects.requireNonNull(insertedCount);
            });
        }));
    }

    @Override
    public Optional<RunSummary> findRun(RunKey key) {
        return Optional.ofNullable(template.query(GET_RUN_SQL, ps -> {
            ps.setString(1, key.runId());
            ps.setString(2, key.project());
        }, rs -> {
            if (!rs.next()) {
                return null;
            }
            return new RunSummary(
                    key,
                    new RunInfo(
                            rs.getString("ci_pipeline_id"),
                            rs.getString("ci_commit_sha"),
                            rs.getString("ci_ref"),
                            rs.getString("ci_project_path"),
                            rs.getString("ci_merge_request_iid")),
                    getInstant(rs.getTimestamp("started_at")),
                    getInstant(rs.getTimestamp("finished_at")),
                    getInstant(rs.getTimestamp("received_at")));
        }));
    }

    private Instant getInstant(Timestamp ts) {
        return (ts != null) ? ts.toInstant() : null;
    }

    @Override
    public List<TestResult> findResults(RunKey key) {
        return template.query(GET_TEST_RESULT_SQL, ps -> {
            ps.setString(1, key.runId());
            ps.setString(2, key.project());
        }, rs -> {
            List<TestResult> testResults = new ArrayList<>();
            while (rs.next()) {
                TestResult result = new TestResult(
                        new PartInfo(
                                rs.getString("ci_job_id"),
                                rs.getString("ci_job_url"),
                                rs.getString("ci_job_name"),
                                rs.getString("host"),
                                rs.getString("adapter_version"),
                                rs.getString("tms_run_id")),
                        rs.getInt("schema_version"),
                        rs.getString("uuid"),
                        rs.getString("external_id"),
                        List.of((String[]) rs.getArray("work_item_ids").getArray()),
                        rs.getString("namespace"),
                        rs.getString("class_name"),
                        rs.getString("name"),
                        rs.getString("title"),
                        rs.getString("description"),
                        rs.getString("message"),
                        TestStatus.valueOf(rs.getString("status")),
                        getInstant(rs.getTimestamp("started_at")),
                        getInstant(rs.getTimestamp("stopped_at")),
                        List.of((String[]) rs.getArray("labels").getArray()),
                        JsonUtils.fromJson(rs.getString("links"), new TypeReference<>() {
                        }),
                        JsonUtils.fromJson(rs.getString("parameters"), new TypeReference<>() {
                        }),
                        JsonUtils.stepsFromJson(rs.getString("steps")),
                        JsonUtils.attachmentsFromJson(rs.getString("attachments")),
                        rs.getString("failure_kind") == null ? null :
                                new Failure(
                                        FailureKind.valueOf(rs.getString("failure_kind")),
                                        rs.getString("failure_type"),
                                        rs.getString("failure_message"),
                                        rs.getString("failure_trace"),
                                        JsonUtils.fromJson(rs.getString("failure_suppressed"), new TypeReference<>() {
                                        })
                                )
                );
                testResults.add(result);
            }
            return testResults;
        });
    }
}
