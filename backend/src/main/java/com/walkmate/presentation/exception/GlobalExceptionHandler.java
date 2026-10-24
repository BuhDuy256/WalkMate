package com.walkmate.presentation.exception;

import com.walkmate.domain.shared.exception.DomainException;
import com.walkmate.domain.tracking.TrackingContractException;
import com.walkmate.domain.tracking.TrackingErrorCode;
import com.walkmate.domain.tracking.TrackingStreamConflictException;
import com.walkmate.domain.walkintent.WalkIntentErrorCode;
import com.walkmate.presentation.dto.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * v2 GPS sync contract violations: the HTTP status is driven by each
     * {@link TrackingErrorCode#defaultStatus()} (409 conflict/flipped
     * stream, 410 expired, 413 batch too large, 422 point invalid) so
     * Android's queue can apply the correct retry policy.
     */
    @ExceptionHandler(TrackingContractException.class)
    public ResponseEntity<ApiResponse<Void>> handleTrackingContractException(
            TrackingContractException ex, HttpServletRequest request) {
        TrackingErrorCode code = ex.getErrorCode() instanceof TrackingErrorCode te ? te : null;
        HttpStatus status = code != null ? code.defaultStatus() : HttpStatus.CONFLICT;
        log.warn("TrackingContractException [{}] at {} {}: {}",
                code, request.getMethod(), request.getRequestURI(), ex.getMessage());
        ApiResponse<Void> body = ApiResponse.error(code != null ? code.getCode() : "TRACKING_CONTRACT_ERROR", ex.getMessage());
        return ResponseEntity.status(status).body(body);
    }

    /**
     * A different stream for the same (session, user) was already allocated.
     * The client must treat this as a lost-device / stream-reset situation.
     */
    @ExceptionHandler(TrackingStreamConflictException.class)
    public ResponseEntity<ApiResponse<Void>> handleTrackingStreamConflictException(
            TrackingStreamConflictException ex, HttpServletRequest request) {
        log.warn("TrackingStreamConflictException at {} {}: {}",
                request.getMethod(), request.getRequestURI(), ex.getMessage());
        ApiResponse<Void> body = ApiResponse.error(
                TrackingErrorCode.TRACKING_STREAM_CONFLICT.getCode(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiResponse<Void>> handleDomainException(DomainException ex, HttpServletRequest request) {
        log.warn("DomainException [{}] at {} {}: {}",
                ex.getErrorCode(), request.getMethod(), request.getRequestURI(), ex.getMessage());

        ApiResponse<Void> body = ApiResponse.error(ex.getErrorCode(), ex.getMessage());

        // Onboarding gate: incomplete profile blocks matching with a 403 so the Android
        // client can navigate to the onboarding flow rather than showing a generic error.
        if (ex.getErrorCode() == WalkIntentErrorCode.PROFILE_INCOMPLETE_FOR_MATCHING) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
        }

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
        public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException ex,
                                           HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining(", "));

        log.warn("Validation failed at {} {}: {}",
            request.getMethod(), request.getRequestURI(), message);

        ApiResponse<Void> body = ApiResponse.error("VALIDATION_ERROR", message);
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(body);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex,
                                                                   HttpServletRequest request) {
        log.warn("IllegalArgument at {} {}: {}",
                request.getMethod(), request.getRequestURI(), ex.getMessage());

        ApiResponse<Void> body = ApiResponse.error("INVALID_ARGUMENT", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFound(NoResourceFoundException ex,
                                                                   HttpServletRequest request) {
        log.warn("Resource not found at {} {}: {}",
                request.getMethod(), request.getRequestURI(), ex.getMessage());

        ApiResponse<Void> body = ApiResponse.error("NOT_FOUND", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex,
                                                                      HttpServletRequest request) {
        log.warn("Method not allowed at {} {}: {}",
                request.getMethod(), request.getRequestURI(), ex.getMessage());

        ApiResponse<Void> body = ApiResponse.error("METHOD_NOT_ALLOWED", ex.getMessage());
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error [{}] at {} {}: {}",
                ex.getClass().getSimpleName(), request.getMethod(), request.getRequestURI(), ex.getMessage(), ex);

        ApiResponse<Void> body = ApiResponse.error("INTERNAL_ERROR", "An internal error occurred.");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}