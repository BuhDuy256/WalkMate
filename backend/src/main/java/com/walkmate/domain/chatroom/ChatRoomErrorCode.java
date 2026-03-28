package com.walkmate.domain.chatroom;

import com.walkmate.domain.shared.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ChatRoomErrorCode implements ErrorCode {

    // §9.3 — Core error codes
    CHAT_ROOM_NOT_FOUND("Chat room not found"),
    CHAT_ROOM_CLOSED("Chat room is closed — no new messages are permitted"),
    CHAT_NOT_PARTICIPANT("Sender is not a participant in this chat room"),
    CHAT_MESSAGE_BLANK("Message content must not be blank"),

    // §9.7 — Read receipt error codes
    CHAT_READ_RECEIPT_FORBIDDEN("Only the recipient may mark a message as read"),
    CHAT_MESSAGE_NOT_FOUND("Message not found in this chat room");

    private final String message;

    @Override
    public String getCode() {
        return name();
    }
}
