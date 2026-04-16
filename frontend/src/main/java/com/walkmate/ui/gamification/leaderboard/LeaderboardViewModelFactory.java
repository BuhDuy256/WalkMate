package com.walkmate.ui.gamification.leaderboard;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.walkmate.data.datasource.remote.api.SessionManager;
import com.walkmate.domain.gamification.GamificationRepository;

/**
 * Manual DI factory for LeaderboardViewModel.
 *
 * Wired in LeaderboardFragment.setupViewModel() using singletons from
 * WalkMateApplication.
 *
 * Usage:
 *   WalkMateApplication app = (WalkMateApplication) requireActivity().getApplication();
 *   LeaderboardViewModelFactory factory = new LeaderboardViewModelFactory(
 *       app.getGamificationRepository(),
 *       app.getSessionManager());
 *   viewModel = new ViewModelProvider(this, factory).get(LeaderboardViewModel.class);
 */
public class LeaderboardViewModelFactory implements ViewModelProvider.Factory {

    private final GamificationRepository gamificationRepository;
    private final String                 myUserId;

    public LeaderboardViewModelFactory(GamificationRepository gamificationRepository,
                                       SessionManager sessionManager) {
        this.gamificationRepository = gamificationRepository;
        this.myUserId               = sessionManager.getUserId();
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(LeaderboardViewModel.class)) {
            return (T) new LeaderboardViewModel(gamificationRepository, myUserId);
        }
        throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
    }
}
