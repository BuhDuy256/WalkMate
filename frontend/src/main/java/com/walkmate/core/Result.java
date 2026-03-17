package com.walkmate.core;

public class Result<T> {
    private final T data;
    private final Throwable error;

    private Result(T data, Throwable error) {
        this.data = data;
        this.error = error;
    }

    public static <T> Result<T> success(T data) {
        return new Result<>(data, null);
    }

    public static <T> Result<T> error(Throwable error) {
        return new Result<>(null, error);
    }

    public boolean isSuccess() {
        return error == null;
    }

    public T getData() {
        return data;
    }

    public Throwable getError() {
        return error;
    }
}
