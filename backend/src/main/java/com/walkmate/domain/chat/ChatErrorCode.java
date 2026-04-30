package com.walkmate.domain.chat;

import com.walkmate.domain.shared.exception.ErrorCode;

public enum ChatErrorCode implements ErrorCode {

    CHAT_UNAUTHORIZED("CHAT_UNAUTHORIZED", "User is not a participant in this session"),
    CHAT_ROOM_CLOSED("CHAT_ROOM_CLOSED",   "This chat room is no longer accepting messages");

    private final String code;
    private final String message;

    ChatErrorCode(String code, String message) {
        this.code    = code;
        this.message = message;
    }

    @Override public String getCode()    { return code; }
    @Override public String getMessage() { return message; }
}
