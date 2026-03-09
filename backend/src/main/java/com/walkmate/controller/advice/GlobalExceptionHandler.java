package com.walkmate.controller.advice;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.Map;
import java.util.HashMap;

/**
 * Global Exception Handler to capture all application errors before they reach the client.
 * This ensures clean JSON responses and proper logging with TraceId.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Handle business logic violations (Domain Exceptions).
     * Typically returning 400 Bad Request since the client did something invalid.
     * We log as WARN because it's not a server crash, just bad client state.
     */
    @ExceptionHandler(RuntimeException.class) // Temporary placeholder for actual DomainExceptions
    public ResponseEntity<Map<String, String>> handleBusinessException(IllegalArgumentException ex) {
        log.warn("Business rule violation / Invalid Argument: {}", ex.getMessage());
        return createErrorResponse("BAD_REQUEST", ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    /**
     * Handle unexpected server crashes (NPE, DB connectivity, syntax errors).
     * These represent actual bugs in the codebase.
     * We log as ERROR and must print the full stack trace to allow Sentry/developers to fix it.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleSystemException(Exception ex) {
        log.error("Unexpected System Error! Investigate TraceID in logs.", ex);
        return createErrorResponse("INTERNAL_SERVER_ERROR", "An unexpected error occurred. Please contact support.", HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private ResponseEntity<Map<String, String>> createErrorResponse(String code, String message, HttpStatus status) {
        Map<String, String> response = new HashMap<>();
        response.put("error", code);
        response.put("message", message);
        response.put("traceId", MDC.get("traceId")); // Ensure client also sees the TraceId!
        
        return new ResponseEntity<>(response, status);
    }
}
