package com.walkmate.ui.chat;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import com.walkmate.domain.chat.ChatMessage;
import com.walkmate.domain.chat.ChatRepository;

import java.util.List;

public class ChatViewModel extends ViewModel {

    private final ChatRepository chatRepository;

    public ChatViewModel(ChatRepository chatRepository) {
        this.chatRepository = chatRepository;
    }

    public void startChat(String sessionId, String currentUserId) {
        chatRepository.connect(sessionId, currentUserId);
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
