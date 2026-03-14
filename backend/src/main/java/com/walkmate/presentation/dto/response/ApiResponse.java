package com.walkmate.presentation.dto.response;

public record ApiResponse(
        String code,
        String message
) {

    public static ApiResponse success(String message) {
        return new ApiResponse("success", message);
    }

    public static ApiResponse error(String message) {
        return new ApiResponse("error", message);
    }
}