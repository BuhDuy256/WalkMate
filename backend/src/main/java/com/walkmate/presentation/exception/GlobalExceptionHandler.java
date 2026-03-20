package com.walkmate.presentation.exception;

import com.walkmate.domain.shared.exception.DomainException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Global exception handler
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ErrorResponse> handleDomainException(DomainException ex) {
        HttpStatus status = switch (ex.getErrorCode()) {
            case "RATING_SESSION_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "RATING_ALREADY_EXISTS" -> HttpStatus.CONFLICT;
            case "RATING_SESSION_NOT_COMPLETED", "RATING_INVALID_SCORE" -> HttpStatus.BAD_REQUEST;
            case "RATING_UNAUTHORIZED" -> HttpStatus.FORBIDDEN;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };

        ErrorResponse error = new ErrorResponse(
                ex.getErrorCode(),
                ex.getMessage(),
                LocalDateTime.now()
        );

        return ResponseEntity.status(status).body(error);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                errors.put(error.getField(), error.getDefaultMessage())
        );

        ErrorResponse error = new ErrorResponse(
                "VALIDATION_ERROR",
                "Invalid request: " + errors,
                LocalDateTime.now()
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    public static record ErrorResponse(String code, String message, LocalDateTime timestamp) {
    }
}
