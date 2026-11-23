package by.syrnik.aimcp.core.history.records;

import by.syrnik.aimcp.core.history.enums.TestStatus;

import java.time.Instant;
import java.util.*;

public record Step(String name, String description, TestStatus status, Instant startedAt, Instant stoppedAt,
                   Map<String, String> parameters,
                   List<String> attachments, Failure failure, List<Step> steps) {

    public Step {
        Checks.requireNonBlank(name, "name");
        Objects.requireNonNull(status, "status");
        parameters = Collections.unmodifiableMap(new LinkedHashMap<>(parameters));
        attachments = List.copyOf(attachments);
        steps = List.copyOf(steps);
    }
}
