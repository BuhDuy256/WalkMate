package com.walkmate.ui.profile.publicprofile;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.walkmate.domain.gamification.GamificationRepository;
import com.walkmate.domain.review.ReviewRepository;
import com.walkmate.domain.social.SocialRepository;

/**
 * Manual DI factory for PublicProfileViewModel.
 *
 * Usage in PublicProfileFragment.onViewCreated():
 *   WalkMateApplication app = (WalkMateApplication) requireActivity().getApplication();
 *   PublicProfileViewModelFactory factory = new PublicProfileViewModelFactory(
 *       app.getSocialRepository(),
 *       app.getGamificationRepository(),
 *       app.getReviewRepository(),
 *       app.getSessionManager().getUserId());
 *   viewModel = new ViewModelProvider(this, factory).get(PublicProfileViewModel.class);
 *
 * Note: Phase 14 will migrate this to Hilt.
 */
public class PublicProfileViewModelFactory implements ViewModelProvider.Factory {

    private final SocialRepository       socialRepo;
    private final GamificationRepository gamificationRepo;
    private final ReviewRepository       reviewRepo;
    private final String                 localUserId;

    public PublicProfileViewModelFactory(SocialRepository socialRepo,
                                         GamificationRepository gamificationRepo,
                                         ReviewRepository reviewRepo,
                                         String localUserId) {
        this.socialRepo       = socialRepo;
        this.gamificationRepo = gamificationRepo;
        this.reviewRepo       = reviewRepo;
        this.localUserId      = localUserId;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(PublicProfileViewModel.class)) {
            return (T) new PublicProfileViewModel(
                    socialRepo, gamificationRepo, reviewRepo, localUserId);
        }
        throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
    }
}
