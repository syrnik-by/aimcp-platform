package by.syrnik.aimcp.runner.http;

import by.syrnik.aimcp.core.masking.MaskingPort;
import by.syrnik.aimcp.runner.tools.Envelope;
import by.syrnik.aimcp.runner.tools.ErrorCode;
import by.syrnik.aimcp.runner.tools.ToolException;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.Objects;

@RestControllerAdvice
public class ErrorAdvice {

    private static final org.slf4j.Logger log = LoggerFactory.getLogger(ErrorAdvice.class);

    private final ResponseWriter responseWriter;
    private final MaskingPort maskingPort;

    public ErrorAdvice(ResponseWriter writer, MaskingPort masking) {
        this.responseWriter = Objects.requireNonNull(writer, "writer");
        this.maskingPort = Objects.requireNonNull(masking, "masking");
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<byte[]> notFound() {
        return responseWriter.toEntity(Envelope.error(new ToolException(ErrorCode.NOT_FOUND, "путь отсутствует"), maskingPort));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<byte[]> methodNotAllowed(HttpRequestMethodNotSupportedException e) {
        ResponseEntity<byte[]> responseEntity =  responseWriter.toEntity(Envelope.error(new ToolException(ErrorCode.METHOD_NOT_ALLOWED, "метод не поддерживается"), maskingPort));
        responseEntity.getHeaders().setAllow(Objects.requireNonNull(e.getSupportedHttpMethods()));
        return responseEntity;
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<byte[]> internal(Exception e) {
        log.error("Ошибка вызова метода : {}", e.getClass().getName());
        return responseWriter.toEntity(Envelope.error(new ToolException(ErrorCode.INTERNAL, ""), maskingPort));
    }
}
