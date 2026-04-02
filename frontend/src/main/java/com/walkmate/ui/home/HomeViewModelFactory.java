package com.walkmate.ui.home;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.walkmate.domain.notification.NotificationRepository;
import com.walkmate.domain.user.UserProfileRepository;
import com.walkmate.domain.user.UserRepository;
import com.walkmate.domain.walksession.WalkSessionRepository;

/**
 * Manual DI factory for HomeViewModel.
 *
 * Instantiated in HomeFragment.onViewCreated() using singletons from
 * WalkMateApplication, keeping the ViewModel free of Context dependencies.
 *
 * Usage:
 *   WalkMateApplication app = (WalkMateApplication) requireActivity().getApplication();
 *   HomeViewModelFactory factory = new HomeViewModelFactory(
 *       app.getWalkSessionRepository(), app.getUserRepository(),
 *       app.getUserProfileRepository(), app.getNotificationRepository());
 *   viewModel = new ViewModelProvider(this, factory).get(HomeViewModel.class);
 */
public class HomeViewModelFactory implements ViewModelProvider.Factory {

    private final WalkSessionRepository sessionRepo;
    private final UserRepository userRepo;
    private final UserProfileRepository profileRepo;
    private final NotificationRepository notificationRepo;

    public HomeViewModelFactory(WalkSessionRepository sessionRepo,
                                UserRepository userRepo,
                                UserProfileRepository profileRepo,
                                NotificationRepository notificationRepo) {
        this.sessionRepo = sessionRepo;
        this.userRepo = userRepo;
        this.profileRepo = profileRepo;
        this.notificationRepo = notificationRepo;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(HomeViewModel.class)) {
            return (T) new HomeViewModel(sessionRepo, userRepo, profileRepo, notificationRepo);
        }
        throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
    }
}
