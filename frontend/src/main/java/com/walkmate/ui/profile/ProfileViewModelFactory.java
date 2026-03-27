package com.walkmate.ui.profile;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import com.walkmate.domain.profile.ProfileService;

import java.util.UUID;

public class ProfileViewModelFactory implements ViewModelProvider.Factory {
    private final ProfileService profileService;
    private final UUID profileOwnerId;
    private final UUID viewerId;

    public ProfileViewModelFactory(ProfileService profileService, UUID profileOwnerId, UUID viewerId) {
        this.profileService = profileService;
        this.profileOwnerId = profileOwnerId;
        this.viewerId = viewerId;
    }

    @NonNull
    @Override
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(ProfileViewModel.class)) {
            return (T) new ProfileViewModel(profileService, profileOwnerId, viewerId);
        }
        throw new IllegalArgumentException("Unknown ViewModel class");
    }
}
