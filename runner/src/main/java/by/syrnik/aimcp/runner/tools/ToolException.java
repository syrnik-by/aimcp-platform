package by.syrnik.aimcp.runner.tools;

import java.util.Objects;

public class ToolException extends RuntimeException {

    private final ErrorCode code;

    public ToolException(ErrorCode code, String message) {
        super(message);
        this.code = Objects.requireNonNull(code, "code");
    }

    public ToolException(ErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = Objects.requireNonNull(code, "code");
    }

    public ErrorCode code() {
        return this.code;
    }
}
