package com.walkmate.ui.rating;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.walkmate.domain.rating.RatingService;

import java.util.UUID;

/**
 * Factory for RatingViewModel
 */
public class RatingViewModelFactory implements ViewModelProvider.Factory {
    private final RatingService ratingService;
    private final RatingViewData initialData;
    private final UUID currentUserId;

    public RatingViewModelFactory(RatingService ratingService, RatingViewData initialData, UUID currentUserId) {
        this.ratingService = ratingService;
        this.initialData = initialData;
        this.currentUserId = currentUserId;
    }

    @NonNull
    @Override
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(RatingViewModel.class)) {
            return (T) new RatingViewModel(ratingService, initialData, currentUserId);
        }
        throw new IllegalArgumentException("Unknown ViewModel class");
    }
}
