package com.walkmate.domain.shared;

public interface DomainCallback<T> {
    void onSuccess(T result);
    void onError(Exception error);
}
