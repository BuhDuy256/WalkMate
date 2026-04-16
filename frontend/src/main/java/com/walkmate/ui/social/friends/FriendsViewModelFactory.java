package com.walkmate.ui.social.friends;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.walkmate.domain.social.SocialRepository;

public class FriendsViewModelFactory implements ViewModelProvider.Factory {

    private final SocialRepository socialRepository;

    public FriendsViewModelFactory(SocialRepository socialRepository) {
        this.socialRepository = socialRepository;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(FriendsViewModel.class)) {
            return (T) new FriendsViewModel(socialRepository);
        }
        throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
    }
}
