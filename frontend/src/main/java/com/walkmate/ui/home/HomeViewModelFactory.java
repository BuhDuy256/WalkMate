package com.walkmate.ui.home;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.walkmate.domain.gamification.GamificationRepository;
import com.walkmate.domain.hotspot.HotspotRepository;
import com.walkmate.domain.notification.NotificationRepository;
import com.walkmate.domain.social.SocialRepository;
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
 *       app.getUserProfileRepository(), app.getNotificationRepository(),
 *       app.getHotspotRepository(), app.getGamificationRepository(),
 *       app.getSocialRepository());
 *   viewModel = new ViewModelProvider(this, factory).get(HomeViewModel.class);
 *
 * Note: Phase 14 will migrate this to Hilt. Constructor dependencies listed
 * here are the source of truth for the injection graph.
 */
public class HomeViewModelFactory implements ViewModelProvider.Factory {

    private final WalkSessionRepository  sessionRepo;
    private final UserRepository         userRepo;
    private final UserProfileRepository  profileRepo;
    private final NotificationRepository notificationRepo;
    private final HotspotRepository      hotspotRepo;
    private final GamificationRepository gamificationRepo;
    private final SocialRepository       socialRepo;

    public HomeViewModelFactory(WalkSessionRepository sessionRepo,
                                UserRepository userRepo,
                                UserProfileRepository profileRepo,
                                NotificationRepository notificationRepo,
                                HotspotRepository hotspotRepo,
                                GamificationRepository gamificationRepo,
                                SocialRepository socialRepo) {
        this.sessionRepo      = sessionRepo;
        this.userRepo         = userRepo;
        this.profileRepo      = profileRepo;
        this.notificationRepo = notificationRepo;
        this.hotspotRepo      = hotspotRepo;
        this.gamificationRepo = gamificationRepo;
        this.socialRepo       = socialRepo;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(HomeViewModel.class)) {
            return (T) new HomeViewModel(sessionRepo, userRepo, profileRepo, notificationRepo,
                    hotspotRepo, gamificationRepo, socialRepo);
        }
        throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
    }
}
