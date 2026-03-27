package com.ecommerce.common.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BaseException.class)
    public ResponseEntity<ErrorResponse> handleBaseException(BaseException ex) {
        log.error("Application error code={} message={}", ex.getErrorCode(), ex.getMessage());
        return ResponseEntity
            .status(ex.getHttpStatus())
            .body(new ErrorResponse(ex.getErrorCode(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors()
            .forEach(e -> errors.put(e.getField(), e.getDefaultMessage()));
        log.warn("Validation failed: {}", errors);
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(new ErrorResponse("VALIDATION_FAILED", "Validation failed", errors));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        // Industry standard: provide the OpenTelemetry traceId if available, fallback to random UUID
        String traceId = org.slf4j.MDC.get("traceId");
        String ref = (traceId != null && !traceId.isBlank()) ? traceId : UUID.randomUUID().toString();
        
        log.error("Unhandled exception ref={}", ref, ex);
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new ErrorResponse("INTERNAL_ERROR",
                "An unexpected error occurred. Reference: " + ref));
    }

    public record ErrorResponse(
        String code,
        String message,
        Map<String, String> fieldErrors,
        Instant timestamp
    ) {
        public ErrorResponse(String code, String message) {
            this(code, message, null, Instant.now());
        }
        public ErrorResponse(String code, String message, Map<String, String> fieldErrors) {
            this(code, message, fieldErrors, Instant.now());
        }
    }
}
