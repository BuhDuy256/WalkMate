package com.walkmate.ui.notification;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.walkmate.domain.notification.NotificationRepository;

public class NotificationViewModelFactory implements ViewModelProvider.Factory {

    private final NotificationRepository notificationRepository;

    public NotificationViewModelFactory(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(NotificationViewModel.class)) {
            return (T) new NotificationViewModel(notificationRepository);
        }
        throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
    }
}
