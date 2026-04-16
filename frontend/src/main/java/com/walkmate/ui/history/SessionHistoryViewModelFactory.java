package com.walkmate.ui.history;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.walkmate.domain.user.UserProfileRepository;
import com.walkmate.domain.walksession.WalkSessionRepository;

/**
 * Manual DI factory for SessionHistoryViewModel.
 *
 * Usage:
 *   WalkMateApplication app = (WalkMateApplication) requireActivity().getApplication();
 *   SessionHistoryViewModelFactory factory = new SessionHistoryViewModelFactory(
 *       app.getWalkSessionRepository(), app.getUserProfileRepository());
 *   viewModel = new ViewModelProvider(this, factory).get(SessionHistoryViewModel.class);
 */
public class SessionHistoryViewModelFactory implements ViewModelProvider.Factory {

    private final WalkSessionRepository sessionRepo;
    private final UserProfileRepository profileRepo;

    public SessionHistoryViewModelFactory(WalkSessionRepository sessionRepo,
                                          UserProfileRepository profileRepo) {
        this.sessionRepo = sessionRepo;
        this.profileRepo = profileRepo;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(SessionHistoryViewModel.class)) {
            return (T) new SessionHistoryViewModel(sessionRepo, profileRepo);
        }
        throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
    }
}
