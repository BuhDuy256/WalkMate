package com.walkmate.domain.chatroom;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

/**
 * Test fixture factory for ChatRoom domain tests.
 *
 * All time-sensitive tests must reference the constants here — never call
 * Instant.now() or System.currentTimeMillis() inside a test.
 */
public class ChatRoomFixture {

    // -------------------------------------------------------------------------
    // Identity constants
    // -------------------------------------------------------------------------

    public static final String CHAT_ROOM_ID  = "room-001";
    public static final String SESSION_ID    = "session-001";
    public static final String PARTICIPANT_A = "user-a";
    public static final String PARTICIPANT_B = "user-b";

    /** A user that is NOT a participant in the room — used for negative tests. */
    public static final String OUTSIDER_ID = "user-outsider";

    // -------------------------------------------------------------------------
    // Clock / time constants
    // -------------------------------------------------------------------------

    /**
     * The single fixed point-in-time used by all factory methods.
     * Passing FIXED_CLOCK to the entity ensures close_at is deterministic.
     */
    public static final Instant FIXED_NOW =
            Instant.parse("2025-06-15T10:00:00Z");

    public static final Clock FIXED_CLOCK =
            Clock.fixed(FIXED_NOW, ZoneOffset.UTC);

    /**
     * The close_at recorded on a room that was already closed before the
     * current test instant.  Used to verify idempotency: a second close()
     * call must NOT overwrite this value.
     */
    public static final Instant ORIGINAL_CLOSE_AT =
            FIXED_NOW.minusSeconds(60);

    // -------------------------------------------------------------------------
    // Factory methods — one per ChatRoom state
    // -------------------------------------------------------------------------

    /**
     * A ChatRoom in {@code OPEN} state.
     * sendMessage() and close() are legal to call.
     */
    public static ChatRoom openChatRoom() {
        return new ChatRoom(
                CHAT_ROOM_ID,
                SESSION_ID,
                PARTICIPANT_A,
                PARTICIPANT_B,
                ChatRoomStatus.OPEN,
                null,
                FIXED_CLOCK
        );
    }

    /**
     * A ChatRoom in {@code CLOSED} state (terminal).
     * close_at is set to ORIGINAL_CLOSE_AT so idempotency tests can assert
     * the value does not change on a redundant close() call.
     */
    public static ChatRoom closedChatRoom() {
        return new ChatRoom(
                CHAT_ROOM_ID,
                SESSION_ID,
                PARTICIPANT_A,
                PARTICIPANT_B,
                ChatRoomStatus.CLOSED,
                ORIGINAL_CLOSE_AT,
                FIXED_CLOCK
        );
    }
}
