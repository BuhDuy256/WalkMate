package com.walkmate.ui.home;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.walkmate.domain.gamification.GamificationRepository;
import com.walkmate.domain.notification.NotificationRepository;
import com.walkmate.domain.user.UserProfileRepository;
import com.walkmate.domain.user.UserRepository;
import com.walkmate.domain.walksession.WalkSessionRepository;

public class HomeViewModelFactory implements ViewModelProvider.Factory {

    private final WalkSessionRepository  sessionRepo;
    private final UserRepository         userRepo;
    private final UserProfileRepository  profileRepo;
    private final NotificationRepository notificationRepo;
    private final GamificationRepository gamificationRepo;

    public HomeViewModelFactory(WalkSessionRepository sessionRepo,
                                UserRepository userRepo,
                                UserProfileRepository profileRepo,
                                NotificationRepository notificationRepo,
                                GamificationRepository gamificationRepo) {
        this.sessionRepo      = sessionRepo;
        this.userRepo         = userRepo;
        this.profileRepo      = profileRepo;
        this.notificationRepo = notificationRepo;
        this.gamificationRepo = gamificationRepo;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(HomeViewModel.class)) {
            return (T) new HomeViewModel(sessionRepo, userRepo, profileRepo,
                    notificationRepo, gamificationRepo);
        }
        throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
    }
}
