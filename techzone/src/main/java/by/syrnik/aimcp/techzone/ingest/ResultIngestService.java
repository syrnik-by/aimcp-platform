package by.syrnik.aimcp.techzone.ingest;

import by.syrnik.aimcp.core.history.RunHistoryPort;
import by.syrnik.aimcp.core.history.records.RunInfo;
import by.syrnik.aimcp.core.history.records.RunKey;
import by.syrnik.aimcp.core.history.records.TestResult;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public class ResultIngestService {

    private static final Logger log = LoggerFactory.getLogger(ResultIngestService.class);
    private final RunHistoryPort runHistoryPort;
    private final ResultFileParser resultFileParser;

    public ResultIngestService(RunHistoryPort historyPort, ResultFileParser parser) {
        this.runHistoryPort = historyPort;
        this.resultFileParser = parser;
    }

    public IngestReport ingest(String project, List<JsonNode> documents) {
        int received = documents.size();
        int added = 0;
        int skipped = 0;

        List<ParsedResult> parsedResults = new ArrayList<>();
        for (int i = 0; i < documents.size(); i++) {
            try {
                Optional<ParsedResult> o = resultFileParser.parse(documents.get(i));
                if (o.isEmpty()) {
                    skipped++;
                    log.warn("документ N{}, проект {} не обработан: версия формата не поддерживается, пропущен", i + 1, project);
                } else {
                    parsedResults.add(o.get());
                }
            } catch (InvalidResultFileException e) {
                throw new InvalidResultFileException("документ N" + (i + 1) + ": " + e.getMessage(), e);
            }
        }

        Map<ParsedResultKey, List<TestResult>> grouped = parsedResults.stream()
                .collect(Collectors.groupingBy(
                        r -> new ParsedResultKey(r.runId(), r.runInfo()),
                        LinkedHashMap::new,
                        Collectors.mapping(ParsedResult::testResult, Collectors.toList())
                ));

        for (Map.Entry<ParsedResultKey, List<TestResult>> entry : grouped.entrySet()) {
            int saved = runHistoryPort.save(new RunKey(project, entry.getKey().runId()), entry.getKey().runInfo(), entry.getValue());
            added += saved;
            skipped += entry.getValue().size() - saved;
        }

        return new IngestReport(received, added, skipped);
    }

    private record ParsedResultKey(String runId, RunInfo runInfo) {
    }
}
