package by.syrnik.aimcp.core.history.records;

public record Suppressed(String type, String message) {

    public Suppressed {
        Checks.requireNonBlank(type, "type");
    }
}
