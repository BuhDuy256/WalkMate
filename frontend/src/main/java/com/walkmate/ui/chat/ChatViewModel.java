package com.walkmate.ui.chat;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import com.walkmate.domain.chat.ChatMessage;
import com.walkmate.domain.chat.ChatRepository;
import com.walkmate.domain.shared.DomainCallback;

import java.util.List;

public class ChatViewModel extends ViewModel {

    private final ChatRepository chatRepository;

    public ChatViewModel(ChatRepository chatRepository) {
        this.chatRepository = chatRepository;
    }

    /**
     * Entry point called by ChatFragment.
     *
     * Flow:
     *  1. loadHistory → seeds the messages LiveData with past messages.
     *  2. connect    → opens the WebSocket; new messages append to the same list.
     *
     * History failure is non-fatal: if the REST call fails (no network, 4xx, etc.)
     * the WebSocket is still opened and live chat works on an empty slate.
     */
    public void startChat(String sessionId, String currentUserId) {
        chatRepository.loadHistory(sessionId, currentUserId, new DomainCallback<List<ChatMessage>>() {
            @Override
            public void onSuccess(List<ChatMessage> messages) {
                chatRepository.connect(sessionId, currentUserId);
            }

            @Override
            public void onError(Exception error) {
                // History unavailable — connect anyway so live chat still works.
                chatRepository.connect(sessionId, currentUserId);
            }
        });
    }

    public void sendMessage(String content) {
        if (content == null || content.trim().isEmpty()) return;
        chatRepository.sendMessage(content.trim());
    }

    public LiveData<List<ChatMessage>> getMessages() {
        return chatRepository.getMessages();
    }

    public LiveData<ChatRepository.ConnectionState> getConnectionState() {
        return chatRepository.getConnectionState();
    }

    @Override
    protected void onCleared() {
        chatRepository.disconnect();
    }
}
