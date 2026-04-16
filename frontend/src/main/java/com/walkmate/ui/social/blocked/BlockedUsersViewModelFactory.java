package com.walkmate.ui.social.blocked;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.walkmate.domain.social.SocialRepository;

public class BlockedUsersViewModelFactory implements ViewModelProvider.Factory {

    private final SocialRepository socialRepository;

    public BlockedUsersViewModelFactory(SocialRepository socialRepository) {
        this.socialRepository = socialRepository;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(BlockedUsersViewModel.class)) {
            return (T) new BlockedUsersViewModel(socialRepository);
        }
        throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
    }
}
