package com.walkmate.domain.chatroom;

import com.walkmate.domain.shared.DomainCallback;

/**
 * Repository contract for the ChatRoom domain.
 *
 * Implementation lives in data/repository/ChatRoomRepositoryImpl.java.
 *
 * Pure Java — zero android.* or androidx.* imports.
 * All methods are async; results are delivered via DomainCallback on the calling thread.
 */
public interface ChatRoomRepository {

    /**
     * Fetches the ChatRoom (with its messages) associated with the given session.
     *
     * @param sessionId  the WalkSession ID whose ChatRoom to load
     * @param callback   delivers ChatRoom on success, or Exception on error
     */
    void getRoom(String sessionId, DomainCallback<ChatRoom> callback);

    /**
     * Sends a message to the ChatRoom for the given session.
     *
     * Guards (CHAT_ROOM_CLOSED, CHAT_NOT_PARTICIPANT, CHAT_MESSAGE_BLANK) are
     * enforced server-side; the returned ChatMessage reflects the persisted state.
     *
     * @param sessionId  the WalkSession ID
     * @param content    message text (must not be blank)
     * @param callback   delivers the created ChatMessage on success, or Exception on error
     */
    void sendMessage(String sessionId, String content, DomainCallback<ChatMessage> callback);
}
