package com.walkmate.domain.rating;

/**
 * Domain exception for Rating (Frontend)
 */
public class RatingException extends Exception {
    private final RatingErrorCode errorCode;

    public RatingException(RatingErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public RatingException(RatingErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public RatingErrorCode getErrorCode() {
        return errorCode;
    }
}
