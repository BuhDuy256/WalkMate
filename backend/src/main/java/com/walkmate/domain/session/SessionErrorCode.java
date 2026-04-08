package com.walkmate.domain.session;

import com.walkmate.domain.shared.exception.ErrorCode;

public enum SessionErrorCode implements ErrorCode {

    SESSION_NOT_FOUND("SESSION_NOT_FOUND", "Session not found"),
    SESSION_NOT_PARTICIPANT("SESSION_NOT_PARTICIPANT", "You are not a participant in this session"),
    SESSION_NOT_PENDING("SESSION_NOT_PENDING", "Session must be in PENDING status for this operation"),
    SESSION_NOT_ACTIVE("SESSION_NOT_ACTIVE", "Session must be in ACTIVE status for this operation"),
    SESSION_ALREADY_TERMINAL("SESSION_ALREADY_TERMINAL", "Session is already in a terminal state"),
    SESSION_ACTIVATION_WINDOW_CLOSED("SESSION_ACTIVATION_WINDOW_CLOSED", "The activation window for this session is closed"),
    SESSION_CANCEL_NOT_PENDING("SESSION_CANCEL_NOT_PENDING", "Only PENDING sessions can be cancelled"),
    SESSION_COMPLETE_TOO_EARLY("SESSION_COMPLETE_TOO_EARLY", "Session cannot be completed yet — minimum walk duration has not been met"),
    SESSION_OVERLAPPING("SESSION_OVERLAPPING", "User already has an overlapping active session"),
    SESSION_NOT_FINISHED("SESSION_NOT_FINISHED", "Route data is only available for finished sessions");

    private final String code;
    private final String message;

    SessionErrorCode(String code, String message) {
        this.code    = code;
        this.message = message;
    }

    @Override public String getCode()    { return code; }
    @Override public String getMessage() { return message; }
}
