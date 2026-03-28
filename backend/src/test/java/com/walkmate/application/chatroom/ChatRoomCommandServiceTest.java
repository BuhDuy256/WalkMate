package com.walkmate.application.chatroom;

import com.walkmate.domain.chatroom.ChatMessage;
import com.walkmate.domain.chatroom.ChatRoom;
import com.walkmate.domain.chatroom.ChatRoomErrorCode;
import com.walkmate.domain.chatroom.ChatRoomRepository;
import com.walkmate.domain.chatroom.ChatRoomStatus;
import com.walkmate.domain.shared.exception.DomainException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static com.walkmate.domain.chatroom.ChatRoomFixture.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

/**
 * Application-layer tests for {@link ChatRoomCommandService}.
 *
 * Scope: orchestration only — load → call entity → save.
 * Domain logic (guard order, state transitions) is covered by ChatRoomTest and is NOT repeated here.
 *
 * Per TESTING.md §3: the domain entity is never mocked; it is always a real object from ChatRoomFixture.
 */
@ExtendWith(MockitoExtension.class)
class ChatRoomCommandServiceTest {

    @Mock
    ChatRoomRepository chatRoomRepository;

    @InjectMocks
    ChatRoomCommandService commandService;

    // =========================================================================
    // sendMessage
    // =========================================================================

    /** (a) Happy path — repo returns an OPEN room; entity accepts the message; save() is called. */
    @Test
    void sendMessage_shouldSaveRoom_whenRoomIsOpenAndSenderIsParticipant() {
        ChatRoom room = openChatRoom();
        given(chatRoomRepository.findById(CHAT_ROOM_ID)).willReturn(Optional.of(room));

        commandService.sendMessage(new SendChatMessageCommand(CHAT_ROOM_ID, PARTICIPANT_A, "Hello!"));

        then(chatRoomRepository).should().save(room);
    }

    /** (b) Entity not found — repo returns empty; service must throw CHAT_ROOM_NOT_FOUND; save() never called. */
    @Test
    void sendMessage_shouldThrowDomainException_whenChatRoomNotFound() {
        given(chatRoomRepository.findById(CHAT_ROOM_ID)).willReturn(Optional.empty());

        DomainException ex = assertThrows(DomainException.class,
                () -> commandService.sendMessage(
                        new SendChatMessageCommand(CHAT_ROOM_ID, PARTICIPANT_A, "Hello!")));

        assertThat(ex.getErrorCode()).isEqualTo(ChatRoomErrorCode.CHAT_ROOM_NOT_FOUND);
        then(chatRoomRepository).should(never()).save(any());
    }

    /** (c) Domain failure bubbles — repo returns a CLOSED room; entity rejects; save() never called. */
    @Test
    void sendMessage_shouldPropagateDomainException_whenEntityRejectsSendMessage() {
        given(chatRoomRepository.findById(CHAT_ROOM_ID)).willReturn(Optional.of(closedChatRoom()));

        assertThrows(DomainException.class,
                () -> commandService.sendMessage(
                        new SendChatMessageCommand(CHAT_ROOM_ID, PARTICIPANT_A, "Too late!")));

        then(chatRoomRepository).should(never()).save(any());
    }

    // =========================================================================
    // closeChatRoomBySession
    // =========================================================================

    /** (a) Happy path — repo returns an OPEN room; entity closes it; save() is called with CLOSED room. */
    @Test
    void closeChatRoomBySession_shouldSaveClosedRoom_whenRoomIsOpen() {
        ChatRoom room = openChatRoom();
        given(chatRoomRepository.findBySessionId(SESSION_ID)).willReturn(Optional.of(room));

        commandService.closeChatRoomBySession(new CloseChatRoomBySessionCommand(SESSION_ID));

        assertThat(room.getStatus()).isEqualTo(ChatRoomStatus.CLOSED);
        then(chatRoomRepository).should().save(room);
    }

    /** (b) Entity not found — repo returns empty; service must throw CHAT_ROOM_NOT_FOUND; save() never called. */
    @Test
    void closeChatRoomBySession_shouldThrowDomainException_whenChatRoomNotFound() {
        given(chatRoomRepository.findBySessionId(SESSION_ID)).willReturn(Optional.empty());

        DomainException ex = assertThrows(DomainException.class,
                () -> commandService.closeChatRoomBySession(
                        new CloseChatRoomBySessionCommand(SESSION_ID)));

        assertThat(ex.getErrorCode()).isEqualTo(ChatRoomErrorCode.CHAT_ROOM_NOT_FOUND);
        then(chatRoomRepository).should(never()).save(any());
    }

    /**
     * (c) Terminal state — repo returns an already-CLOSED room.
     *
     * {@link ChatRoom#close()} is idempotent by contract (§9.4): CLOSED → CLOSED is a silent no-op,
     * so no DomainException is thrown.  The service must still persist the unchanged room, which
     * ensures the infrastructure lock is always released cleanly.
     * This test verifies the service does not short-circuit or suppress the idempotent path.
     */
    @Test
    void closeChatRoomBySession_shouldSaveIdempotently_whenRoomIsAlreadyClosed() {
        ChatRoom room = closedChatRoom();
        given(chatRoomRepository.findBySessionId(SESSION_ID)).willReturn(Optional.of(room));

        commandService.closeChatRoomBySession(new CloseChatRoomBySessionCommand(SESSION_ID));

        assertThat(room.getStatus()).isEqualTo(ChatRoomStatus.CLOSED);
        assertThat(room.getCloseAt()).isEqualTo(ORIGINAL_CLOSE_AT); // close_at must NOT be overwritten
        then(chatRoomRepository).should().save(room);
    }

    // =========================================================================
    // markMessagesAsRead
    // =========================================================================

    /**
     * (a) Happy path — repo returns an OPEN room with a message from PARTICIPANT_B;
     * PARTICIPANT_A (recipient) marks it as read; save() is called.
     *
     * The message is produced by the real entity (sendMessage) so the test
     * never constructs domain objects inline.
     */
    @Test
    void markMessagesAsRead_shouldSaveRoom_whenReaderIsRecipientAndMessageExists() {
        ChatRoom room = openChatRoom();
        room.sendMessage(PARTICIPANT_B, "Hey there!"); // message sent by B
        ChatMessage message = room.getMessages().get(0);

        given(chatRoomRepository.findById(CHAT_ROOM_ID)).willReturn(Optional.of(room));
        given(chatRoomRepository.findMessagesAfter(CHAT_ROOM_ID, null))
                .willReturn(List.of(message));

        commandService.markMessagesAsRead(
                new MarkChatMessagesReadCommand(CHAT_ROOM_ID, PARTICIPANT_A, message.getId()));

        then(chatRoomRepository).should().save(room);
    }

    /** (b) Entity not found — repo returns empty; service must throw CHAT_ROOM_NOT_FOUND; save() never called. */
    @Test
    void markMessagesAsRead_shouldThrowDomainException_whenChatRoomNotFound() {
        given(chatRoomRepository.findById(CHAT_ROOM_ID)).willReturn(Optional.empty());

        DomainException ex = assertThrows(DomainException.class,
                () -> commandService.markMessagesAsRead(
                        new MarkChatMessagesReadCommand(CHAT_ROOM_ID, PARTICIPANT_A, "msg-001")));

        assertThat(ex.getErrorCode()).isEqualTo(ChatRoomErrorCode.CHAT_ROOM_NOT_FOUND);
        then(chatRoomRepository).should(never()).save(any());
    }

    /**
     * (c) Domain failure bubbles — message buffer is empty after pre-load; entity throws
     * because lastReadMessageId does not exist; save() never called.
     *
     * This exercises the service's non-catching contract: if the entity rejects the operation,
     * the DomainException must propagate unmodified to the caller.
     */
    @Test
    void markMessagesAsRead_shouldPropagateDomainException_whenEntityRejectsReadReceipt() {
        ChatRoom room = openChatRoom();
        given(chatRoomRepository.findById(CHAT_ROOM_ID)).willReturn(Optional.of(room));
        given(chatRoomRepository.findMessagesAfter(CHAT_ROOM_ID, null)).willReturn(List.of());

        assertThrows(DomainException.class,
                () -> commandService.markMessagesAsRead(
                        new MarkChatMessagesReadCommand(CHAT_ROOM_ID, PARTICIPANT_A, "nonexistent-msg-id")));

        then(chatRoomRepository).should(never()).save(any());
    }
}
