package com.walkmate.domain.chatroom;

import java.util.List;
import java.util.Optional;

/**
 * Persistence contract for the ChatRoom aggregate.
 *
 * Implementations live in {@code infrastructure/repository/chatroom/} and must use JDBC.
 * No Spring, HTTP, or framework imports belong here.
 *
 * CQRS note (§9.5 W2, §9.6):
 *  findById / findBySessionId implementations MUST acquire a pessimistic row-lock
 *  (SELECT … FOR UPDATE) when loading for a write operation so that ChatRoom status
 *  is re-evaluated inside the same JDBC transaction as the INSERT/UPDATE.
 */
public interface ChatRoomRepository {

    /**
     * Loads a ChatRoom by its owning WalkSession ID.
     * Returns empty when no room exists for that session (maps to CHAT_ROOM_NOT_FOUND).
     */
    Optional<ChatRoom> findBySessionId(String sessionId);

    /**
     * Loads a ChatRoom by its own primary key.
     * Returns empty when not found (maps to CHAT_ROOM_NOT_FOUND).
     */
    Optional<ChatRoom> findById(String chatRoomId);

    /**
     * Persists the ChatRoom state (status, close_at) and all messages currently
     * in the aggregate's in-session buffer ({@link ChatRoom#getMessages()}).
     */
    ChatRoom save(ChatRoom chatRoom);

    /**
     * Loads messages in this room that were created after {@code afterMessageId},
     * ordered by {@code (created_at ASC, message_id ASC)} (§9.8 ordering rule).
     *
     * Used by:
     *  - The catch-up HTTP endpoint after client reconnection (§9.8).
     *  - The application service to hydrate the aggregate before a read-receipt
     *    operation ({@link ChatRoom#markMessagesAsRead}).
     *
     * Pass {@code null} for {@code afterMessageId} to load from the beginning.
     */
    List<ChatMessage> findMessagesAfter(String chatRoomId, String afterMessageId);
}
