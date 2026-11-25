package by.syrnik.aimcp.runner.tools;

import static java.util.Objects.requireNonNull;

public class ToolException extends RuntimeException {

    private final ErrorCode code;

    public ToolException(ErrorCode code, String message) {
        super(message);
        this.code = requireNonNull(code, "code");
    }

    public ToolException(ErrorCode code, String message, Throwable cause) {
        super(requireNonNull(message), cause);
        this.code = requireNonNull(code, "code");
    }

    public ErrorCode code() {
        return this.code;
    }
}
