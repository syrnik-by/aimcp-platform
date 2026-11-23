package by.syrnik.aimcp.techzone.controllers;

import by.syrnik.aimcp.techzone.ingest.IngestReport;
import by.syrnik.aimcp.techzone.ingest.InvalidResultFileException;
import by.syrnik.aimcp.techzone.ingest.ResultIngestService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/projects/{project}/results")
public class ResultsController {

    private final ResultIngestService resultIngestService;

    public ResultsController(ResultIngestService resultIngestService) {
        this.resultIngestService = resultIngestService;
    }

    @PostMapping
    public IngestReport ingest(@PathVariable String project, @RequestBody List<JsonNode> documents) {
        if (project.isBlank()) {
            throw new InvalidResultFileException("project: не может быть пустым");
        }
        return resultIngestService.ingest(project, documents);
    }

    @ExceptionHandler(InvalidResultFileException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> invalidResultFileExceptionHandler(InvalidResultFileException e) {
        return Map.of("error", e.getMessage());
    }
}
