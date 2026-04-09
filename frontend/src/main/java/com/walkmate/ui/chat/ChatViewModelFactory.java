package com.walkmate.ui.chat;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.walkmate.domain.chat.ChatRepository;

public class ChatViewModelFactory implements ViewModelProvider.Factory {

    private final ChatRepository chatRepository;

    public ChatViewModelFactory(ChatRepository chatRepository) {
        this.chatRepository = chatRepository;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(ChatViewModel.class)) {
            return (T) new ChatViewModel(chatRepository);
        }
        throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
    }
}
