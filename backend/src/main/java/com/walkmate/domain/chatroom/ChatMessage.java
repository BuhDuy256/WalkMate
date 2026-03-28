package com.walkmate.domain.chatroom;

import lombok.Getter;

import java.time.Instant;

/**
 * An immutable message entity within the ChatRoom aggregate.
 *
 * Invariants (§9.7):
 *  - read_at may only be set once and only by the recipient (enforced by ChatRoom).
 *  - read_at is display metadata only — it has no effect on access control.
 */
@Getter
public class ChatMessage {

    private final String id;
    private final String chatRoomId;
    private final String senderId;
    private final String content;
    private final Instant createdAt;
    private Instant readAt;

    /**
     * Creation constructor — package-private so only {@link ChatRoom#sendMessage} can produce
     * a ChatMessage.  This enforces invariant §9.2.3 at the aggregate boundary.
     */
    ChatMessage(String id, String chatRoomId, String senderId, String content, Instant createdAt) {
        this.id = id;
        this.chatRoomId = chatRoomId;
        this.senderId = senderId;
        this.content = content;
        this.createdAt = createdAt;
        this.readAt = null;
    }

    /**
     * Rehydration constructor — called by the repository when loading from DB.
     * {@code readAt} may be {@code null} for unread messages.
     */
    public ChatMessage(String id, String chatRoomId, String senderId, String content,
                       Instant createdAt, Instant readAt) {
        this.id = id;
        this.chatRoomId = chatRoomId;
        this.senderId = senderId;
        this.content = content;
        this.createdAt = createdAt;
        this.readAt = readAt;
    }

    /**
     * Stamps this message as read at {@code now}.  Package-private: only the ChatRoom
     * aggregate root may call this to guarantee §9.7.1 (recipient-only, set-once).
     * Already-read messages are silently ignored.
     */
    void markAsRead(Instant now) {
        if (this.readAt == null) {
            this.readAt = now;
        }
    }
}
