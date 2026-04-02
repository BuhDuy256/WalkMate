package com.walkmate.ui.profile;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.walkmate.domain.user.UserProfileRepository;

/**
 * Manual DI factory for ProfileViewModel.
 *
 * Instantiated in ProfileFragment.onViewCreated() using the UserProfileRepository
 * singleton from WalkMateApplication.
 *
 * Usage:
 *   WalkMateApplication app = (WalkMateApplication) requireActivity().getApplication();
 *   ProfileViewModelFactory factory = new ProfileViewModelFactory(app.getUserProfileRepository());
 *   viewModel = new ViewModelProvider(this, factory).get(ProfileViewModel.class);
 */
public class ProfileViewModelFactory implements ViewModelProvider.Factory {

    private final UserProfileRepository profileRepo;

    public ProfileViewModelFactory(UserProfileRepository profileRepo) {
        this.profileRepo = profileRepo;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(ProfileViewModel.class)) {
            return (T) new ProfileViewModel(profileRepo);
        }
        throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
    }
}
