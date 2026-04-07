package com.walkmate.domain.walkintent;

import com.walkmate.domain.shared.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum WalkIntentErrorCode implements ErrorCode {
    INTENT_NOT_FOUND("Walk intent not found"),
    INTENT_ALREADY_CANCELLED("Walk intent is already cancelled"),
    INTENT_ALREADY_CONSUMED("Walk intent is already matched/consumed"),
    INTENT_NOT_OPEN("Walk intent must be OPEN for this operation"),
    INTENT_NOT_MATCHING("Walk intent must be MATCHING for this operation"),
    INTENT_NOT_OWNER("You can only cancel your own intent"),
    INTENT_OVERLAPPING("An active intent already exists in this time window"),
    INTENT_OVERLAPPING_SESSION("An active or pending session already exists in this time window"),
    INVALID_INTENT_DATA("Invalid walk intent data provided"),
    INVALID_TIME_RANGE("Time start must be before time end"),
    INVALID_AGE_RANGE("Age min must be less than or equal to age max"),
    INTENT_PRIVATE_FRIEND_NOT_ACCEPTED("Private intent requires an ACCEPTED friendship with the invited user");

    private final String message;

    @Override
    public String getCode() {
        return name();
    }
}
