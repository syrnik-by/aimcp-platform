package by.syrnik.aimcp.core.history.records;

import by.syrnik.aimcp.core.history.enums.FailureKind;

import java.util.List;
import java.util.Objects;

public record Failure(FailureKind kind, String type, String message, String trace, List<Suppressed> suppressed) {

    public Failure {
        Objects.requireNonNull(kind, "kind");
        Checks.requireNonBlank(type, "type");
        Objects.requireNonNull(trace, "trace");
        suppressed = List.copyOf(suppressed);
    }
}
