package by.syrnik.aimcp.core.history.records;

import by.syrnik.aimcp.core.history.enums.TestStatus;

import java.time.Instant;
import java.util.*;

import static by.syrnik.aimcp.core.history.records.Checks.requireNonBlank;

public record TestResult(PartInfo part, int schemaVersion, String uuid, String externalId,
                         List<String> workItemIds, String namespace, String className, String name, String title,
                         String description,
                         String message, TestStatus status, Instant startedAt, Instant stoppedAt,
                         List<String> labels,
                         List<Link> links, Map<String, String> parameters, List<Step> steps, List<String> attachments,
                         Failure failure) {

    public TestResult {
        Objects.requireNonNull(part, "part");
        Objects.requireNonNull(status, "status");
        requireNonBlank(uuid, "uuid");
        requireNonBlank(externalId, "externalId");
        requireNonBlank(name, "name");
        workItemIds = List.copyOf(workItemIds);
        labels = List.copyOf(labels);
        links = List.copyOf(links);
        steps = List.copyOf(steps);
        attachments = List.copyOf(attachments);
        parameters = Collections.unmodifiableMap(new LinkedHashMap<>(parameters));
    }

}
