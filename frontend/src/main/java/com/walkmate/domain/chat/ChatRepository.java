package com.walkmate.domain.chat;

import androidx.lifecycle.LiveData;

import com.walkmate.domain.shared.DomainCallback;

import java.util.List;

public interface ChatRepository {

    /**
     * Fetches message history from the REST endpoint and seeds the messages
     * LiveData before opening the WebSocket. Call this before {@link #connect}.
     * Non-blocking — result delivered via callback on a background thread.
     * Failure is soft: callback.onError fires but the caller should still connect.
     */
    void loadHistory(String sessionId, String currentUserId,
                     DomainCallback<List<ChatMessage>> callback);

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
