package com.walkmate.domain.social;

import com.walkmate.domain.shared.exception.ErrorCode;

public enum SocialErrorCode implements ErrorCode {
    FOLLOW_ALREADY_FOLLOWING("Already following this user"),
    FOLLOW_SELF_FOLLOW_FORBIDDEN("Cannot follow yourself"),
    BLOCK_ALREADY_BLOCKED("User is already blocked"),
    BLOCK_SELF_BLOCK_FORBIDDEN("Cannot block yourself"),
    SOCIAL_USER_NOT_FOUND("User not found");

    private final String message;

    SocialErrorCode(String message) {
        this.message = message;
    }

    @Override
    public String getCode() {
        return name();
    }

    @Override
    public String getMessage() {
        return message;
    }
}
