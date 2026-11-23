package by.syrnik.aimcp.techzone.ingest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/**
 * Документы контракта результатов для тестов. Базовый документ — пример из docs/results-file-format.md.
 */
public final class ResultDocuments {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ResultDocuments() {
    }

    /** Пример из контракта; каждый вызов возвращает новую копию, её можно портить. */
    public static ObjectNode example() {
        try (InputStream in = ResultDocuments.class.getResourceAsStream("/ingest/result-v1.json")) {
            return (ObjectNode) MAPPER.readTree(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Пример с заданными идентификатором прогона и uuid теста. */
    public static ObjectNode of(String runId, String uuid) {
        ObjectNode document = example();
        ((ObjectNode) document.get("run")).put("runId", runId);
        ((ObjectNode) document.get("test")).put("uuid", uuid);
        return document;
    }
}
