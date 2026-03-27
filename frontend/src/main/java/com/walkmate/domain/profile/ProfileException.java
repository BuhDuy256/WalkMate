package com.walkmate.domain.profile;

public class ProfileException extends Exception {
    private final ProfileErrorCode errorCode;

    public ProfileException(ProfileErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ProfileException(ProfileErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public ProfileErrorCode getErrorCode() {
        return errorCode;
    }
}
