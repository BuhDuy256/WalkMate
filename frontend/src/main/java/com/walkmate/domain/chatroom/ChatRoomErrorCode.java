package com.walkmate.domain.chatroom;

/**
 * Domain error codes for the ChatRoom aggregate (mirrors backend ChatRoomErrorCode).
 *
 * Pure Java — zero android.* or androidx.* imports.
 */
public enum ChatRoomErrorCode {

    /** No ChatRoom found for the given session ID. */
    CHAT_ROOM_NOT_FOUND,

    /** sendMessage() was called on a CLOSED room. */
    CHAT_ROOM_CLOSED,

    /** Sender is not participantA or participantB. */
    CHAT_NOT_PARTICIPANT,

    /** Message content is blank after trimming. */
    CHAT_MESSAGE_BLANK
}
