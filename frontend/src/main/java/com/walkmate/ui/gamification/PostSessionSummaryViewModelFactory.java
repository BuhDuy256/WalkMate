package com.walkmate.ui.gamification;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.walkmate.domain.gamification.GamificationRepository;
import com.walkmate.domain.walksession.WalkSessionRepository;

/**
 * Manual DI factory for PostSessionSummaryViewModel.
 */
public class PostSessionSummaryViewModelFactory implements ViewModelProvider.Factory {

    private final GamificationRepository gamificationRepo;
    private final WalkSessionRepository  sessionRepo;

    public PostSessionSummaryViewModelFactory(GamificationRepository gamificationRepo,
                                              WalkSessionRepository sessionRepo) {
        this.gamificationRepo = gamificationRepo;
        this.sessionRepo      = sessionRepo;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(PostSessionSummaryViewModel.class)) {
            return (T) new PostSessionSummaryViewModel(gamificationRepo, sessionRepo);
        }
        throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
    }
}
