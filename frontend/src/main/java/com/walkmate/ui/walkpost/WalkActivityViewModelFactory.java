package com.walkmate.ui.walkpost;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.walkmate.domain.walkpost.WalkPostRepository;

public class WalkActivityViewModelFactory implements ViewModelProvider.Factory {

    private final WalkPostRepository walkPostRepository;

    public WalkActivityViewModelFactory(WalkPostRepository walkPostRepository) {
        this.walkPostRepository = walkPostRepository;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(WalkActivityViewModel.class)) {
            return (T) new WalkActivityViewModel(walkPostRepository);
        }
        throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
    }
}
