package by.syrnik.aimcp.techzone.controllers;

import by.syrnik.aimcp.techzone.ingest.ResultDocuments;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "aimcp.history.store=memory")
@AutoConfigureMockMvc
class ResultsControllerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mvc;

    @Test
    void batchIsAcceptedAndNewResultsAreCounted() throws Exception {
        String project = newProject();
        String runId = UUID.randomUUID().toString();

        ingest(project, batch(ResultDocuments.of(runId, "u-1"), ResultDocuments.of(runId, "u-2")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.received").value(2))
                .andExpect(jsonPath("$.added").value(2))
                .andExpect(jsonPath("$.skipped").value(0));
    }

    @Test
    void repeatedBatchAddsNothing() throws Exception {
        String project = newProject();
        JsonNode batch = batch(ResultDocuments.of(UUID.randomUUID().toString(), "u-1"));
        ingest(project, batch).andExpect(status().isOk());

        ingest(project, batch)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.received").value(1))
                .andExpect(jsonPath("$.added").value(0))
                .andExpect(jsonPath("$.skipped").value(1));
    }

    @Test
    void documentWithUnknownSchemaVersionIsSkipped() throws Exception {
        ObjectNode future = ResultDocuments.of(UUID.randomUUID().toString(), "u-1");
        future.put("schemaVersion", 2);

        ingest(newProject(), batch(future))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.added").value(0))
                .andExpect(jsonPath("$.skipped").value(1));
    }

    @Test
    void corruptedDocumentGivesBadRequestWithTheReason() throws Exception {
        ObjectNode broken = ResultDocuments.of(UUID.randomUUID().toString(), "u-1");
        ((ObjectNode) broken.get("test")).remove("uuid");

        ingest(newProject(), batch(broken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", startsWith("документ N1: /test/uuid:")));
    }

    @Test
    void rejectedBatchSavesNothing() throws Exception {
        String project = newProject();
        String runId = UUID.randomUUID().toString();
        ObjectNode good = ResultDocuments.of(runId, "u-1");
        ObjectNode broken = ResultDocuments.of(runId, "u-2");
        ((ObjectNode) broken.get("test")).remove("status");

        ingest(project, batch(good, broken)).andExpect(status().isBadRequest());

        ingest(project, batch(good))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.added").value(1));
    }

    @Test
    void blankProjectGivesBadRequest() throws Exception {
        ingest(" ", batch(ResultDocuments.of(UUID.randomUUID().toString(), "u-1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", startsWith("project:")));
    }

    @Test
    void bodyThatIsNotAnArrayGivesBadRequest() throws Exception {
        ingest(newProject(), ResultDocuments.example())
                .andExpect(status().isBadRequest());
    }

    private ResultActions ingest(String project, JsonNode body) throws Exception {
        return mvc.perform(post("/api/projects/{project}/results", project)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body.toString().getBytes(StandardCharsets.UTF_8)));
    }

    private static ArrayNode batch(JsonNode... documents) {
        ArrayNode array = MAPPER.createArrayNode();
        for (JsonNode document : documents) {
            array.add(document);
        }
        return array;
    }

    // история в памяти общая для всех тестов контекста, поэтому у каждого теста свой проект
    private static String newProject() {
        return "project-" + UUID.randomUUID();
    }
}
