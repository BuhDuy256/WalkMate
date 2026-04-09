package com.walkmate.domain.chat;

import androidx.lifecycle.LiveData;

import java.util.List;

public interface ChatRepository {

    /**
     * Establish a WebSocket connection for the given session.
     * Idempotent — calling when already connected to the same session is a no-op.
     */
    void connect(String sessionId, String currentUserId);

    /** Send a plain-text message. No-op if not connected. */
    void sendMessage(String content);

    /** Gracefully close the WebSocket and clear the message list. */
    void disconnect();

    /** Observable message list — Fragment observes, never mutates. */
    LiveData<List<ChatMessage>> getMessages();

    /** Observable connection state for showing a "Connecting…" indicator. */
    LiveData<ConnectionState> getConnectionState();

    enum ConnectionState {
        CONNECTING, CONNECTED, RECONNECTING, DISCONNECTED, ERROR
    }
}
