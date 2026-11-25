package by.syrnik.aimcp.runner.http;

import by.syrnik.aimcp.core.masking.MaskingPort;
import by.syrnik.aimcp.runner.tools.Envelope;
import by.syrnik.aimcp.runner.tools.ErrorCode;
import by.syrnik.aimcp.runner.tools.ToolException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.Objects;

@RestControllerAdvice
public class ErrorAdvice {

    public ResponseWriter responseWriter;
    public MaskingPort maskingPort;

    public ErrorAdvice(ResponseWriter writer, MaskingPort masking) {
        this.responseWriter = Objects.requireNonNull(writer, "writer");
        this.maskingPort = Objects.requireNonNull(masking, "masking");
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<byte[]> notFound() {
        ResponseWriter.Response response = responseWriter.write(Envelope.error(new ToolException(ErrorCode.UNKNOWN_TOOL, "путь отсутствует"), maskingPort));
        return new ResponseEntity<>(response.body(), HttpStatus.valueOf(response.httpStatus()));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<byte[]> methodNotAllowed(HttpRequestMethodNotSupportedException e) {
        ResponseWriter.Response response = responseWriter.write(Envelope.error(new ToolException(ErrorCode.METHOD_NOT_ALLOWED, e.getMessage()), maskingPort));
        return new ResponseEntity<>(response.body(), HttpStatus.valueOf(response.httpStatus()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<byte[]> internal(Exception e) {
        ResponseWriter.Response response = responseWriter.write(Envelope.error(new ToolException(ErrorCode.INTERNAL, e.getMessage()), maskingPort));
        return new ResponseEntity<>(response.body(), HttpStatus.valueOf(response.httpStatus()));
    }
}
