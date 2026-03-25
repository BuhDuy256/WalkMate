package com.walkmate.presentation.dto.response;

import com.walkmate.domain.shared.exception.ErrorCode;
import java.time.Instant;

public record ApiResponse<T>(
        boolean success,
        T data,
        ErrorDetails error,
        String timestamp) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null, Instant.now().toString());
    }

    public static <T> ApiResponse<T> error(ErrorCode code, String message) {
        return new ApiResponse<>(false, null, new ErrorDetails(code.getCode(), message), Instant.now().toString());
    }

    public static <T> ApiResponse<T> error(String code, String message) {
        return new ApiResponse<>(false, null, new ErrorDetails(code, message), Instant.now().toString());
    }

    public record ErrorDetails(String code, String message) {
    }
}
