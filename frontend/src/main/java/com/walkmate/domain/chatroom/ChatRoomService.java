package com.walkmate.domain.chatroom;

import com.walkmate.domain.shared.DomainCallback;

/**
 * Domain service coordinating ChatRoom use-cases on the client.
 *
 * Business guards run server-side; this service is a thin coordinator that
 * delegates to ChatRoomRepository and can be extended with client-side
 * orchestration (e.g., optimistic updates, retry) without touching the ViewModel.
 *
 * Pure Java — zero android.* or androidx.* imports.
 */
public class ChatRoomService {

    private final ChatRoomRepository repository;

    public ChatRoomService(ChatRoomRepository repository) {
        this.repository = repository;
    }

    /**
     * Loads the ChatRoom for the given session, including all existing messages.
     */
    public void loadRoom(String sessionId, DomainCallback<ChatRoom> callback) {
        repository.getRoom(sessionId, callback);
    }

    /**
     * Sends a message to the ChatRoom for the given session.
     */
    public void sendMessage(String sessionId, String content, DomainCallback<ChatMessage> callback) {
        repository.sendMessage(sessionId, content, callback);
    }
}
