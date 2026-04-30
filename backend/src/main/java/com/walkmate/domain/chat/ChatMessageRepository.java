package com.walkmate.domain.chat;

import java.util.List;

/**
 * Domain-layer port for persisting and querying chat messages.
 * Implementation lives in the infrastructure layer (MongoDB adapter).
 */
public interface ChatMessageRepository {

    /**
     * Persists a new message. Returns the same message unchanged (messageId
     * is already set by {@link ChatMessage#create}).
     */
    ChatMessage save(ChatMessage message);

    /**
     * Returns the most recent {@code limit} messages for the given session,
     * ordered oldest-first (ready for chronological display).
     */
    List<ChatMessage> findLatestBySessionId(String sessionId, int limit);
}
