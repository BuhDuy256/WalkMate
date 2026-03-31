package com.walkmate.domain.chatroom;

/**
 * Lifecycle states of a ChatRoom aggregate (mirrors backend ChatRoom.Status).
 *
 * OPEN  – messages can be sent and read (session is PENDING_MEET or ACTIVE).
 * CLOSED – read-only archive; no new messages permitted.
 */
public enum ChatRoomStatus {
    OPEN,
    CLOSED
}
