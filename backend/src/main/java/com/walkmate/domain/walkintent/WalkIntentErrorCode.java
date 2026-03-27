package com.walkmate.domain.walkintent;

import com.walkmate.domain.shared.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum WalkIntentErrorCode implements ErrorCode {
    INTENT_NOT_FOUND("Walk intent not found", 404),
    INTENT_ALREADY_CANCELLED("Walk intent is already cancelled", 409),
    INTENT_ALREADY_MATCHED("Walk intent is already matched", 409),
    INVALID_INTENT_DATA("Invalid walk intent data provided", 400),
    INVALID_TIME_RANGE("Time start must be before time end", 400),
    INVALID_AGE_RANGE("Age min must be less than or equal to age max", 400);

    private final String message;
    private final int httpStatus;

    @Override
    public String getCode() {
        return name();
    }
}
