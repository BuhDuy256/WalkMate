package com.walkmate.domain.chatroom;

import com.walkmate.domain.shared.exception.DomainException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.walkmate.domain.chatroom.ChatRoomFixture.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Domain-layer tests for {@link ChatRoom}.
 *
 * Rules enforced:
 *  - No @SpringBootTest
 *  - No Mockito mocks on domain entities
 *  - No try/catch — assertThrows() only
 *  - Every assertThrows is followed by assertThat(ex.getErrorCode())
 *  - Every happy-path test asserts a concrete state change, never just "no exception"
 *
 * Naming: methodName_shouldDoX_whenConditionY
 */
class ChatRoomTest {

    // =========================================================================
    // sendMessage(senderId, content)
    // =========================================================================

    /** (a) Happy path — participant sends a valid message to an open room. */
    @Test
    void sendMessage_shouldAddMessageToRoom_whenSenderIsParticipantAndRoomIsOpen() {
        ChatRoom room = openChatRoom();

        room.sendMessage(PARTICIPANT_A, "Hello there!");

        List<ChatMessage> messages = room.getMessages();
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).getSenderId()).isEqualTo(PARTICIPANT_A);
        assertThat(messages.get(0).getContent()).isEqualTo("Hello there!");
        assertThat(messages.get(0).getCreatedAt()).isEqualTo(FIXED_NOW);
    }

    /** (b) Invariant violation — sender is not a session participant. */
    @Test
    void sendMessage_shouldThrowDomainException_whenSenderIsNotParticipant() {
        ChatRoom room = openChatRoom();

        DomainException ex = assertThrows(DomainException.class,
                () -> room.sendMessage(OUTSIDER_ID, "Hello!"));

        assertThat(ex.getErrorCode()).isEqualTo(ChatRoomErrorCode.CHAT_NOT_PARTICIPANT);
    }

    /** (b) Invariant violation — message content is blank after trimming. */
    @Test
    void sendMessage_shouldThrowDomainException_whenContentIsBlank() {
        ChatRoom room = openChatRoom();

        DomainException ex = assertThrows(DomainException.class,
                () -> room.sendMessage(PARTICIPANT_A, "   "));

        assertThat(ex.getErrorCode()).isEqualTo(ChatRoomErrorCode.CHAT_MESSAGE_BLANK);
    }

    /** (c) Terminal state guard — room is CLOSED; no messages may be sent. */
    @Test
    void sendMessage_shouldThrowDomainException_whenRoomIsClosed() {
        ChatRoom room = closedChatRoom();

        DomainException ex = assertThrows(DomainException.class,
                () -> room.sendMessage(PARTICIPANT_A, "Too late!"));

        assertThat(ex.getErrorCode()).isEqualTo(ChatRoomErrorCode.CHAT_ROOM_CLOSED);
    }

    // =========================================================================
    // close()
    // =========================================================================

    /** (a) Happy path — closing an OPEN room transitions it to CLOSED and stamps close_at. */
    @Test
    void close_shouldTransitionToClosedAndSetCloseAt_whenRoomIsOpen() {
        ChatRoom room = openChatRoom();

        room.close();

        assertThat(room.getStatus()).isEqualTo(ChatRoomStatus.CLOSED);
        assertThat(room.getCloseAt()).isEqualTo(FIXED_NOW);
    }

    /**
     * (b) Idempotency invariant — calling close() on an already-CLOSED room is a silent no-op.
     * The existing close_at must NOT be overwritten.
     */
    @Test
    void close_shouldBeIdempotent_whenRoomIsAlreadyClosed() {
        ChatRoom room = closedChatRoom(); // closeAt == ORIGINAL_CLOSE_AT

        room.close(); // second call — must not throw and must not change state

        assertThat(room.getStatus()).isEqualTo(ChatRoomStatus.CLOSED);
        assertThat(room.getCloseAt()).isEqualTo(ORIGINAL_CLOSE_AT);
    }

    /**
     * (c) Terminal state guard — after close(), the room is terminal;
     * sendMessage() must be rejected with CHAT_ROOM_CLOSED.
     */
    @Test
    void close_shouldPreventNewMessages_whenRoomTransitionsToClosed() {
        ChatRoom room = openChatRoom();
        room.close();

        DomainException ex = assertThrows(DomainException.class,
                () -> room.sendMessage(PARTICIPANT_A, "Still trying"));

        assertThat(ex.getErrorCode()).isEqualTo(ChatRoomErrorCode.CHAT_ROOM_CLOSED);
    }
}
