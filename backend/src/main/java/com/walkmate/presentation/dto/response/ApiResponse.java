package com.walkmate.presentation.dto.response;

public record ApiResponse<T>(
    boolean success,
    T data,
    ErrorDetails error,
    String timestamp
) {
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null, java.time.Instant.now().toString());
    }
    
    public record ErrorDetails(String code, String message) {}
}
