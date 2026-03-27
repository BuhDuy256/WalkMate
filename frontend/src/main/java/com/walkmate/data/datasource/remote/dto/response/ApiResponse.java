package com.walkmate.data.datasource.remote.dto.response;

public class ApiResponse<T> {

    private boolean success;
    private T data;
    private ErrorDetails error;
    private String timestamp;

    public boolean isSuccess() {
        return success;
    }

    public T getData() {
        return data;
    }

    public ErrorDetails getError() {
        return error;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public static class ErrorDetails {
        private String code;
        private String message;

        public String getCode() {
            return code;
        }

        public String getMessage() {
            return message;
        }
    }
}
