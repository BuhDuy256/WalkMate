package com.walkmate.domain.chat;

/**
 * Domain-layer port for managing chat room lifecycle.
 * Implementations live in the infrastructure layer (MongoDB adapter).
 *
 * No MongoDB-specific types appear in this interface — callers depend only on this port.
 */
public interface ChatRoomRepository {

    /**
     * Initialises a new chat room for the given session. Idempotent — calling
     * again for an existing sessionId is a no-op (upsert semantics).
     */
    void initRoom(String sessionId);

    /**
     * Closes the chat room for the given session, enforcing the S-7 write-lock.
     * Idempotent — closing an already-closed room is a no-op.
     */
    void closeRoom(String sessionId);
}