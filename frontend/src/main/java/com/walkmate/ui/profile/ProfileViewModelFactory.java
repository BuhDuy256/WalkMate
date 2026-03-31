package com.walkmate.ui.profile;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.walkmate.domain.user.UserRepository;

/**
 * Manual DI factory for ProfileViewModel.
 *
 * Instantiated in ProfileFragment.onViewCreated() using the UserRepository
 * singleton from WalkMateApplication, keeping ProfileViewModel free of
 * Context dependencies.
 *
 * Usage:
 *   WalkMateApplication app = (WalkMateApplication) requireActivity().getApplication();
 *   ProfileViewModelFactory factory = new ProfileViewModelFactory(app.getUserRepository());
 *   viewModel = new ViewModelProvider(this, factory).get(ProfileViewModel.class);
 */
public class ProfileViewModelFactory implements ViewModelProvider.Factory {

    private final UserRepository userRepo;

    public ProfileViewModelFactory(UserRepository userRepo) {
        this.userRepo = userRepo;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(ProfileViewModel.class)) {
            return (T) new ProfileViewModel(userRepo);
        }
        throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
    }
}
