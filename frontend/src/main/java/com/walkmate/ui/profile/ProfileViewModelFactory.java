package com.walkmate.ui.profile;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.walkmate.data.repository.UserRepositoryImpl;
import com.walkmate.domain.gamification.GamificationRepository;
import com.walkmate.domain.review.ReviewRepository;
import com.walkmate.domain.user.UserProfileRepository;
import com.walkmate.domain.user.UserRepository;

/**
 * Manual DI factory for ProfileViewModel.
 *
 * Instantiated in ProfileFragment.onViewCreated() using singletons from
 * WalkMateApplication, keeping the ViewModel free of Context dependencies.
 *
 * Usage:
 *   WalkMateApplication app = (WalkMateApplication) requireActivity().getApplication();
 *   ProfileViewModelFactory factory = new ProfileViewModelFactory(
 *       app.getUserProfileRepository(), requireContext());
 *   ProfileViewModelFactory factory = new ProfileViewModelFactory(
 *       app.getUserProfileRepository(),
 *       app.getGamificationRepository(),
 *       app.getReviewRepository());
 *   viewModel = new ViewModelProvider(this, factory).get(ProfileViewModel.class);
 *
 * Note: Phase 14 will migrate this to Hilt.
 */
public class ProfileViewModelFactory implements ViewModelProvider.Factory {

    private final UserProfileRepository profileRepo;
    private final UserRepository userRepository;
    private final GamificationRepository gamificationRepo;
    private final ReviewRepository       reviewRepo;

    public ProfileViewModelFactory(UserProfileRepository profileRepo,
                                   Context context,
                                   GamificationRepository gamificationRepo,
                                   ReviewRepository reviewRepo) {
        this.profileRepo = profileRepo;
        this.userRepository = new UserRepositoryImpl(context.getApplicationContext());
        this.gamificationRepo = gamificationRepo;
        this.reviewRepo = reviewRepo;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(ProfileViewModel.class)) {
            return (T) new ProfileViewModel(profileRepo, userRepository, gamificationRepo, reviewRepo);
        }
        throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
    }
}
