package com.walkmate.domain.user;

import com.walkmate.domain.shared.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum UserErrorCode implements ErrorCode {
    USER_NOT_FOUND("User not found"),
    USER_EMAIL_ALREADY_EXISTS("Email already exists"),
    USER_PHONE_ALREADY_EXISTS("Phone number already exists"),
    USER_INVALID_CREDENTIALS("Invalid email or password"),
    INVALID_USER_DATA("Invalid data provided"),
    USER_PROVIDER_CONFLICT("This Google account is already linked to a different WalkMate account"),
    USER_DISPLAY_NAME_BLANK("Display name must not be blank"),
    USER_ALREADY_PRIVATE("User visibility is already PRIVATE"),
    USER_ALREADY_PUBLIC("User visibility is already PUBLIC"),
    USER_INVALID_EMAIL_FORMAT("Email format is invalid"),
    USER_ACCOUNT_SUSPENDED("Account is suspended or banned"),
    USER_OTP_EXPIRED("OTP has expired"),
    USER_OTP_ALREADY_USED("OTP has already been used"),
    USER_OTP_INVALID("OTP code is incorrect"),
    USER_OTP_ATTEMPTS_EXCEEDED("Too many incorrect OTP attempts"),
    USER_OTP_RATE_LIMITED("Please wait before requesting a new OTP");

    private final String message;

    @Override
    public String getCode() {
        return name();
    }
}
