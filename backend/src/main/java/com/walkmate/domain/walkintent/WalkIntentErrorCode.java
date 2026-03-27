package com.walkmate.domain.walkintent;

import com.walkmate.domain.shared.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum WalkIntentErrorCode implements ErrorCode {
    INTENT_NOT_FOUND("Walk intent not found"),
    INTENT_ALREADY_CANCELLED("Walk intent is already cancelled"),
    INTENT_ALREADY_MATCHED("Walk intent is already matched"),
    INVALID_INTENT_DATA("Invalid walk intent data provided"),
    INVALID_TIME_RANGE("Time start must be before time end"),
    INVALID_AGE_RANGE("Age min must be less than or equal to age max");

    private final String message;

    @Override
    public String getCode() {
        return name();
    }
}
