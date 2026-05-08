package com.walkmate.ui.profile.publicprofile;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.walkmate.domain.gamification.GamificationRepository;
import com.walkmate.domain.review.ReviewRepository;
import com.walkmate.domain.social.SocialRepository;
import com.walkmate.domain.walkpost.WalkPostRepository;

public class PublicProfileViewModelFactory implements ViewModelProvider.Factory {

    private final SocialRepository       socialRepo;
    private final GamificationRepository gamificationRepo;
    private final ReviewRepository       reviewRepo;
    private final WalkPostRepository     walkPostRepo;
    private final String                 localUserId;

    public PublicProfileViewModelFactory(SocialRepository socialRepo,
                                         GamificationRepository gamificationRepo,
                                         ReviewRepository reviewRepo,
                                         WalkPostRepository walkPostRepo,
                                         String localUserId) {
        this.socialRepo       = socialRepo;
        this.gamificationRepo = gamificationRepo;
        this.reviewRepo       = reviewRepo;
        this.walkPostRepo     = walkPostRepo;
        this.localUserId      = localUserId;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(PublicProfileViewModel.class)) {
            return (T) new PublicProfileViewModel(
                    socialRepo, gamificationRepo, reviewRepo, walkPostRepo, localUserId);
        }
        throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
    }
}
