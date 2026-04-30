package com.walkmate.ui.badge;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.walkmate.domain.gamification.GamificationRepository;

public class BadgeViewModelFactory implements ViewModelProvider.Factory {

    private final GamificationRepository gamificationRepo;
    private final String                 userId;

    public BadgeViewModelFactory(GamificationRepository gamificationRepo, String userId) {
        this.gamificationRepo = gamificationRepo;
        this.userId           = userId;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(BadgeViewModel.class)) {
            return (T) new BadgeViewModel(gamificationRepo, userId);
        }
        throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
    }
}
