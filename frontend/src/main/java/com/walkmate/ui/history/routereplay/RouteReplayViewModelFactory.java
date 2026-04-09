package com.walkmate.ui.history.routereplay;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.walkmate.domain.walksession.WalkSessionRepository;

/**
 * Manual DI factory for RouteReplayViewModel.
 */
public class RouteReplayViewModelFactory implements ViewModelProvider.Factory {

    private final WalkSessionRepository sessionRepo;

    public RouteReplayViewModelFactory(WalkSessionRepository sessionRepo) {
        this.sessionRepo = sessionRepo;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(RouteReplayViewModel.class)) {
            return (T) new RouteReplayViewModel(sessionRepo);
        }
        throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
    }
}
