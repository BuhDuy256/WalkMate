package com.walkmate.domain.review;

import com.walkmate.domain.shared.exception.ErrorCode;

public enum ReviewErrorCode implements ErrorCode {

    REVIEW_SESSION_NOT_COMPLETED("REVIEW_SESSION_NOT_COMPLETED",
            "A review can only be submitted for a COMPLETED session"),
    REVIEW_NOT_PARTICIPANT("REVIEW_NOT_PARTICIPANT",
            "You were not a participant in this session"),
    REVIEW_ALREADY_SUBMITTED("REVIEW_ALREADY_SUBMITTED",
            "You have already submitted a review for this session"),
    REVIEW_INVALID_RATING("REVIEW_INVALID_RATING",
            "Rating must be between 1 and 5 stars"),
    REVIEW_NOT_FOUND("REVIEW_NOT_FOUND",
            "Review not found");

    private final String code;
    private final String message;

    ReviewErrorCode(String code, String message) {
        this.code    = code;
        this.message = message;
    }

    @Override public String getCode()    { return code; }
    @Override public String getMessage() { return message; }
}
