package com.walkmate.domain.notification;

import com.walkmate.domain.shared.exception.ErrorCode;

public enum NotificationErrorCode implements ErrorCode {

    NOTIFICATION_NOT_FOUND("NOTIFICATION_NOT_FOUND", "Notification not found"),
    NOTIFICATION_NOT_OWNER("NOTIFICATION_NOT_OWNER", "You do not own this notification");

    private final String code;
    private final String message;

    NotificationErrorCode(String code, String message) {
        this.code    = code;
        this.message = message;
    }

    @Override public String getCode()    { return code; }
    @Override public String getMessage() { return message; }
}
