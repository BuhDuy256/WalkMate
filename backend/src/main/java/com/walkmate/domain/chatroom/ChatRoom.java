package com.walkmate.domain.chatroom;

import com.walkmate.domain.shared.exception.DomainException;
import lombok.Getter;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * ChatRoom aggregate root.
 *
 * Lifecycle (§9.1):
 *  OPEN  → messages can be sent and read.
 *  CLOSED → terminal; read-only archive.
 *
 * Invariants (§9.2):
 *  1. Exactly one ChatRoom exists per WalkSession — created atomically with the session.
 *  2. Transitions to CLOSED when its WalkSession reaches a terminal state.
 *  3. Only participantA / participantB may send messages.
 *  4. Message content must not be blank.
 *
 * CQRS note (§9.5 W2 / §9.6):
 *  All domain guards are re-evaluated inside the JDBC transaction by the repository.
 *  The entity itself enforces correctness against its current in-memory state; the
 *  infrastructure layer must acquire a pessimistic lock (SELECT … FOR UPDATE) before
 *  loading this aggregate to prevent stale-read races.
 */
@Getter
public class ChatRoom {

    private final String id;
    private final String sessionId;
    private final String participantA;
    private final String participantB;
    private ChatRoomStatus status;
    private Instant closeAt;
    private final Clock clock;

    /**
     * In-unit-of-work message buffer.
     *
     * Contains ONLY messages appended during the current request (by sendMessage) or
     * pre-loaded by the application service for read-receipt operations.
     * It does NOT hold the full message history — use
     * {@link ChatRoomRepository#findMessagesAfter} for historical queries.
     */
    private final List<ChatMessage> messages;

    /** No-arg constructor for framework hydration — do NOT call directly. */
    protected ChatRoom() {
        this.id = null;
        this.sessionId = null;
        this.participantA = null;
        this.participantB = null;
        this.messages = new ArrayList<>();
        this.clock = Clock.systemUTC();
    }

    /**
     * Rehydration constructor — called by the repository when loading from DB.
     * Starts with an empty message buffer; messages are loaded on demand.
     */
    public ChatRoom(String id, String sessionId,
                    String participantA, String participantB,
                    ChatRoomStatus status, Instant closeAt,
                    Clock clock) {
        this.id = id;
        this.sessionId = sessionId;
        this.participantA = participantA;
        this.participantB = participantB;
        this.status = status;
        this.closeAt = closeAt;
        this.clock = clock;
        this.messages = new ArrayList<>();
    }

    /**
     * Factory method — creates a new OPEN ChatRoom atomically with a WalkSession.
     * The caller (Domain Service / application layer) is responsible for saving both
     * in the same JDBC transaction (§9.6 Invariant 1).
     */
    public static ChatRoom create(String sessionId, String participantA,
                                   String participantB, Clock clock) {
        requireText(sessionId,    "Session ID is required");
        requireText(participantA, "Participant A is required");
        requireText(participantB, "Participant B is required");
        return new ChatRoom(
                UUID.randomUUID().toString(),
                sessionId,
                participantA,
                participantB,
                ChatRoomStatus.OPEN,
                null,
                clock
        );
    }

    // -------------------------------------------------------------------------
    // Business methods (§9.4)
    // -------------------------------------------------------------------------

    /**
     * Appends a new message from {@code senderId} with the given {@code content}.
     *
     * Guards evaluated in contract order:
     *  1. Status must be OPEN         → CHAT_ROOM_CLOSED      (409)
     *  2. Sender must be a participant → CHAT_NOT_PARTICIPANT  (403)
     *  3. Content must not be blank   → CHAT_MESSAGE_BLANK    (400)
     *
     * The created {@link ChatMessage} is added to the in-session buffer.
     * The application service must persist it via {@link ChatRoomRepository#save}.
     */
    public void sendMessage(String senderId, String content) {
        if (this.status == ChatRoomStatus.CLOSED) {
            throw new DomainException(ChatRoomErrorCode.CHAT_ROOM_CLOSED);
        }
        if (!senderId.equals(participantA) && !senderId.equals(participantB)) {
            throw new DomainException(ChatRoomErrorCode.CHAT_NOT_PARTICIPANT);
        }
        if (content == null || content.trim().isEmpty()) {
            throw new DomainException(ChatRoomErrorCode.CHAT_MESSAGE_BLANK);
        }
        ChatMessage message = new ChatMessage(
                UUID.randomUUID().toString(),
                this.id,
                senderId,
                content.trim(),
                clock.instant()
        );
        this.messages.add(message);
    }

    /**
     * Transitions this room to CLOSED and records {@code close_at}.
     *
     * Idempotent: CLOSED → CLOSED is a silent no-op (§9.4).
     * Must be called inside the same JDBC transaction as the WalkSession terminal
     * transition to guarantee atomicity (§9.6 Invariant 1).
     */
    public void close() {
        if (this.status == ChatRoomStatus.CLOSED) {
            return; // idempotent no-op — do NOT overwrite closeAt
        }
        this.status = ChatRoomStatus.CLOSED;
        this.closeAt = clock.instant();
    }

    /**
     * Marks all messages in the in-session buffer up to and including
     * {@code lastReadMessageId} as read by {@code readerId} (§9.7).
     *
     * The application service must pre-load the messages via
     * {@link #loadHistoricalMessages} before calling this method.
     *
     * Guards:
     *  - lastReadMessageId must exist in the buffer → CHAT_MESSAGE_NOT_FOUND (404)
     *  - readerId must NOT be the sender of that message → CHAT_READ_RECEIPT_FORBIDDEN (403)
     */
    public void markMessagesAsRead(String readerId, String lastReadMessageId) {
        ChatMessage lastMessage = messages.stream()
                .filter(m -> m.getId().equals(lastReadMessageId))
                .findFirst()
                .orElseThrow(() -> new DomainException(ChatRoomErrorCode.CHAT_MESSAGE_NOT_FOUND));

        if (lastMessage.getSenderId().equals(readerId)) {
            throw new DomainException(ChatRoomErrorCode.CHAT_READ_RECEIPT_FORBIDDEN);
        }

        Instant now = clock.instant();
        for (ChatMessage message : messages) {
            // Only mark messages where readerId is the recipient (not the sender)
            if (!message.getSenderId().equals(readerId)) {
                message.markAsRead(now);
            }
            if (message.getId().equals(lastReadMessageId)) {
                break;
            }
        }
    }

    /**
     * Pre-loads historical messages into the buffer for read-receipt operations.
     * Called by the application service after fetching messages from the repository.
     * Clears the existing buffer to avoid duplicate entries.
     */
    public void loadHistoricalMessages(List<ChatMessage> historicalMessages) {
        this.messages.clear();
        this.messages.addAll(historicalMessages);
    }

    /** Returns an unmodifiable view of the in-session message buffer. */
    public List<ChatMessage> getMessages() {
        return Collections.unmodifiableList(messages);
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private static void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName);
        }
    }
}
