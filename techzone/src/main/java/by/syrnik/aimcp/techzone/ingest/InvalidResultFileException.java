package by.syrnik.aimcp.techzone.ingest;

public class InvalidResultFileException extends RuntimeException {
    public InvalidResultFileException(String message) { super(message); }
    public InvalidResultFileException(String message, Throwable cause) { super(message, cause); }
}
