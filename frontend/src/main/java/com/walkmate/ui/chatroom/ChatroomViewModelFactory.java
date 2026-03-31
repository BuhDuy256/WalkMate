package com.walkmate.ui.chatroom;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.walkmate.domain.chatroom.ChatRoomService;

/**
 * Manual DI factory for ChatroomViewModel.
 *
 * Usage in ChatroomActivity.onCreate():
 *   WalkMateApplication app = (WalkMateApplication) getApplication();
 *   ChatroomViewModelFactory factory = new ChatroomViewModelFactory(
 *       new ChatRoomService(app.getChatRoomRepository()));
 *   viewModel = new ViewModelProvider(this, factory).get(ChatroomViewModel.class);
 */
public class ChatroomViewModelFactory implements ViewModelProvider.Factory {

    private final ChatRoomService chatRoomService;

    public ChatroomViewModelFactory(ChatRoomService chatRoomService) {
        this.chatRoomService = chatRoomService;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(ChatroomViewModel.class)) {
            return (T) new ChatroomViewModel(chatRoomService);
        }
        throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
    }
}
