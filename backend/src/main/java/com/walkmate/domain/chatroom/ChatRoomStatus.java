package com.walkmate.domain.chatroom;

public enum ChatRoomStatus {
    /** Messages can be sent and read. */
    OPEN,

    /** No new messages permitted. Read-only archive. Terminal state. */
    CLOSED
}
