package com.walkmate.domain.user;

import com.walkmate.domain.shared.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum UserErrorCode implements ErrorCode {
    USER_NOT_FOUND("User not found", 404),
    USER_ALREADY_EXISTS("Email already exists", 409),
    USER_INVALID_CREDENTIALS("Invalid email or password", 401),
    INVALID_USER_DATA("Invalid data provided", 400);

    private final String message;
    private final int httpStatus;

    @Override
    public String getCode() {
        return name();
    }
}
