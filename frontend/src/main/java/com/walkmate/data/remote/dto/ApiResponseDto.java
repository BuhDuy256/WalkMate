package com.walkmate.data.remote.dto;

public class ApiResponseDto<T> {
    public boolean success;
    public T data;
    public ErrorDto error;
    public String timestamp;

    public static class ErrorDto {
        public String code;
        public String message;
    }
}
