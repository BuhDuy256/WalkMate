package com.walkmate.ui.coordination.matching;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

public class MatchingViewModelFactory implements ViewModelProvider.Factory {

    private final String hotspotName;

    public MatchingViewModelFactory(String hotspotName) {
        this.hotspotName = hotspotName;
    }

    @SuppressWarnings("unchecked")
    @NonNull
    @Override
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(MatchingViewModel.class)) {
            return (T) new MatchingViewModel(hotspotName);
        }
        throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
    }
}
