package com.walkmate.infrastructure.exception;

import com.walkmate.domain.user.UserAlreadyExistsException;
import com.walkmate.presentation.dto.response.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<ApiResponse> handleUserAlreadyExists(UserAlreadyExistsException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(exception.getMessage()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class, IllegalArgumentException.class})
    public ResponseEntity<ApiResponse> handleBadRequest(Exception exception) {
        String message = switch (exception) {
            case MethodArgumentNotValidException validationException -> validationException.getBindingResult()
                    .getFieldErrors()
                    .stream()
                    .findFirst()
                    .map(FieldError::getDefaultMessage)
                    .orElse("Invalid request");
            case ConstraintViolationException constraintViolationException -> constraintViolationException.getMessage();
            case IllegalArgumentException illegalArgumentException -> illegalArgumentException.getMessage();
            default -> "Invalid request";
        };

        return ResponseEntity.badRequest().body(ApiResponse.error(message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse> handleUnexpectedError(Exception exception) {
        log.error("Unhandled exception", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Internal server error"));
    }
}