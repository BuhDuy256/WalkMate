package com.walkmate.application.chatroom;

import com.walkmate.domain.chatroom.ChatMessage;
import com.walkmate.domain.chatroom.ChatRoom;
import com.walkmate.domain.chatroom.ChatRoomErrorCode;
import com.walkmate.domain.chatroom.ChatRoomRepository;
import com.walkmate.domain.shared.exception.DomainException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatRoomCommandService {

    private final ChatRoomRepository chatRoomRepository;

    /**
     * Sends a message from a participant to the specified ChatRoom.
     *
     * The JDBC implementation of {@link ChatRoomRepository#findById} MUST acquire a
     * pessimistic row-lock (SELECT … FOR UPDATE) so that the ChatRoom status guard
     * inside {@link ChatRoom#sendMessage} is evaluated against the committed DB state
     * rather than a stale in-memory snapshot (§9.5 W2, §9.6).
     */
    @Transactional
    public void sendMessage(SendChatMessageCommand command) {
        // 1. Load — throws CHAT_ROOM_NOT_FOUND if missing
        ChatRoom chatRoom = chatRoomRepository.findById(command.chatRoomId())
                .orElseThrow(() -> new DomainException(ChatRoomErrorCode.CHAT_ROOM_NOT_FOUND));

        // 2. Entity enforces: room OPEN, sender is participant, content not blank
        chatRoom.sendMessage(command.senderId(), command.content());

        // 3. Persist room state + new message from the in-session buffer
        chatRoomRepository.save(chatRoom);
    }

    /**
     * Closes the ChatRoom associated with the given WalkSession.
     *
     * Must be invoked inside the same transaction as the WalkSession terminal
     * transition to guarantee atomicity (§9.6 Invariant 1).
     * {@link ChatRoom#close()} is idempotent — calling this for an already-CLOSED
     * room is a safe no-op.
     */
    @Transactional
    public void closeChatRoomBySession(CloseChatRoomBySessionCommand command) {
        // 1. Load by session — throws CHAT_ROOM_NOT_FOUND if missing
        ChatRoom chatRoom = chatRoomRepository.findBySessionId(command.sessionId())
                .orElseThrow(() -> new DomainException(ChatRoomErrorCode.CHAT_ROOM_NOT_FOUND));

        // 2. Entity transitions OPEN → CLOSED and stamps close_at; CLOSED → CLOSED is a no-op
        chatRoom.close();

        // 3. Persist updated status and close_at
        chatRoomRepository.save(chatRoom);
    }

    /**
     * Marks all messages up to and including {@code lastReadMessageId} as read
     * by the caller ({@code readerId}).
     *
     * Flow: load room → pre-load messages into aggregate → domain enforces recipient
     * invariant → persist updated read_at values.
     */
    @Transactional
    public void markMessagesAsRead(MarkChatMessagesReadCommand command) {
        // 1. Load room — throws CHAT_ROOM_NOT_FOUND if missing
        ChatRoom chatRoom = chatRoomRepository.findById(command.chatRoomId())
                .orElseThrow(() -> new DomainException(ChatRoomErrorCode.CHAT_ROOM_NOT_FOUND));

        // 1b. Pre-load messages so the aggregate can evaluate the domain guards
        List<ChatMessage> messages = chatRoomRepository.findMessagesAfter(command.chatRoomId(), null);
        chatRoom.loadHistoricalMessages(messages);

        // 2. Entity enforces: lastReadMessageId exists, readerId is not the sender
        chatRoom.markMessagesAsRead(command.readerId(), command.lastReadMessageId());

        // 3. Persist room + messages with updated read_at
        chatRoomRepository.save(chatRoom);
    }
}
