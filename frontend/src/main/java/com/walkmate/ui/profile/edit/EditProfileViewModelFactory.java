package com.walkmate.ui.profile.edit;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.walkmate.domain.user.UserProfileRepository;

/**
 * Manual DI factory for EditProfileViewModel.
 *
 * Usage:
 *   WalkMateApplication app = (WalkMateApplication) requireActivity().getApplication();
 *   EditProfileViewModelFactory factory =
 *       new EditProfileViewModelFactory(app.getUserProfileRepository());
 *   viewModel = new ViewModelProvider(this, factory).get(EditProfileViewModel.class);
 */
public class EditProfileViewModelFactory implements ViewModelProvider.Factory {

    private final UserProfileRepository profileRepo;

    public EditProfileViewModelFactory(UserProfileRepository profileRepo) {
        this.profileRepo = profileRepo;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(EditProfileViewModel.class)) {
            return (T) new EditProfileViewModel(profileRepo);
        }
        throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
    }
}
