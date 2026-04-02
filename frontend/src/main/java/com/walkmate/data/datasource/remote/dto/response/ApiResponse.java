package com.walkmate.data.datasource.remote.dto.response;

public class ApiResponse<T> {

    private boolean success;
    private T data;
    private ApiError error;

    public ApiResponse() {
        // Empty constructor for Gson
    }

    public boolean isSuccess() {
        return success;
    }

    public boolean getSuccess() {
        return success;
    }

    public T getData() {
        return data;
    }

    public ApiError getError() {
        return error;
    }

    public static class ApiError {
        private String code;
        private String message;

        public ApiError() {
            // Empty constructor for Gson
        }

        public String getCode() {
            return code;
        }

        public String getMessage() {
            return message;
        }
    }
}
