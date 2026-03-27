package com.walkmate.domain.user;

import com.walkmate.domain.shared.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum UserErrorCode implements ErrorCode {
    USER_NOT_FOUND("User not found"),
    USER_ALREADY_EXISTS("Email already exists"),
    USER_INVALID_CREDENTIALS("Invalid email or password"),
    INVALID_USER_DATA("Invalid data provided");

    private final String message;

    @Override
    public String getCode() {
        return name();
    }
}
