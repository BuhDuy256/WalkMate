package com.walkmate.ui.profile;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.walkmate.data.repository.UserRepositoryImpl;
import com.walkmate.domain.user.UserProfileRepository;
import com.walkmate.domain.user.UserRepository;

/**
 * Manual DI factory for ProfileViewModel.
 *
 * Usage:
 *   WalkMateApplication app = (WalkMateApplication) requireActivity().getApplication();
 *   ProfileViewModelFactory factory = new ProfileViewModelFactory(
 *       app.getUserProfileRepository(), requireContext());
 *   viewModel = new ViewModelProvider(this, factory).get(ProfileViewModel.class);
 */
public class ProfileViewModelFactory implements ViewModelProvider.Factory {

    private final UserProfileRepository profileRepo;
    private final UserRepository userRepository;

    public ProfileViewModelFactory(UserProfileRepository profileRepo, Context context) {
        this.profileRepo = profileRepo;
        this.userRepository = new UserRepositoryImpl(context.getApplicationContext());
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(ProfileViewModel.class)) {
            return (T) new ProfileViewModel(profileRepo, userRepository);
        }
        throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
    }
}
