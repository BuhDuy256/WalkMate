package com.walkmate.domain.chat;

import java.util.List;

public class ChatUiState {
    private final boolean isLoading;
    private final List<ChatMessage> messages;
    private final ChatRepository.ConnectionState connectionState;
    private final String error;

    public ChatUiState(boolean isLoading, List<ChatMessage> messages,
                       ChatRepository.ConnectionState connectionState, String error) {
        this.isLoading       = isLoading;
        this.messages        = messages;
        this.connectionState = connectionState;
        this.error           = error;
    }

    public boolean isLoading()                           { return isLoading; }
    public List<ChatMessage> getMessages()               { return messages; }
    public ChatRepository.ConnectionState getConnectionState() { return connectionState; }
    public String getError()                             { return error; }
}
