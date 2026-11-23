package by.syrnik.aimcp.runner.tools;

import java.util.Locale;

public enum ErrorCode {
    INVALID_REQUEST(400),
    INVALID_ARGUMENTS(400),
    UNAUTHORIZED(401),
    UNKNOWN_TOOL(404),
    NOT_FOUND(404),
    METHOD_NOT_ALLOWED(405),
    BUSY(409),
    REQUEST_TOO_LARGE(413),
    TOOL_FAILED(500),
    MASKING_FAILED(500),
    RESPONSE_TOO_LARGE(500),
    INTERNAL(500);

    private final int httpStatus;

    ErrorCode(int httpStatus) {
        this.httpStatus = httpStatus;
    }

    public int httpStatus() {
        return this.httpStatus;
    }

    public String code() {
        return this.name().toLowerCase(Locale.ROOT);
    }
}

