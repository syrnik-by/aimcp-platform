package by.syrnik.aimcp.core.history.records;

public record PartInfo(String jobId, String jobUrl, String jobName, String host, String adapterVersion, String tmsRunId) {

    public PartInfo {
        Checks.requireNonBlank(host, "host");
        Checks.requireNonBlank(adapterVersion, "adapterVersion");
    }
}
