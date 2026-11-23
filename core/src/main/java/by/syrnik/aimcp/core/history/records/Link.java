package by.syrnik.aimcp.core.history.records;

public record Link(String type, String title, String url) {

    public Link {
        Checks.requireNonBlank(url, "url");
    }
}
