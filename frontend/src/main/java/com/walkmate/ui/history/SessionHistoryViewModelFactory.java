package com.walkmate.ui.history;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.walkmate.domain.walksession.WalkSessionRepository;

/**
 * Manual DI factory for SessionHistoryViewModel.
 *
 * Usage:
 *   WalkMateApplication app = (WalkMateApplication) requireActivity().getApplication();
 *   String currentUserId = app.getSessionManager().getUserId();
 *   viewModel = new ViewModelProvider(this,
 *       new SessionHistoryViewModelFactory(app.getWalkSessionRepository(), currentUserId))
 *       .get(SessionHistoryViewModel.class);
 */
public class SessionHistoryViewModelFactory implements ViewModelProvider.Factory {

    private final WalkSessionRepository sessionRepo;
    private final String currentUserId;

    public SessionHistoryViewModelFactory(WalkSessionRepository sessionRepo, String currentUserId) {
        this.sessionRepo   = sessionRepo;
        this.currentUserId = currentUserId;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(SessionHistoryViewModel.class)) {
            return (T) new SessionHistoryViewModel(sessionRepo, currentUserId);
        }
        throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
    }
}
