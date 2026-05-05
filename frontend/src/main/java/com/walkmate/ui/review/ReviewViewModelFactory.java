package com.walkmate.ui.review;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.walkmate.domain.review.ReviewRepository;
import com.walkmate.domain.walksession.WalkSessionRepository;

/**
 * Manual DI factory for ReviewViewModel.
 */
public class ReviewViewModelFactory implements ViewModelProvider.Factory {

    private final ReviewRepository reviewRepo;
    private final WalkSessionRepository sessionRepo;

    public ReviewViewModelFactory(ReviewRepository reviewRepo,
            WalkSessionRepository sessionRepo) {
        this.reviewRepo = reviewRepo;
        this.sessionRepo = sessionRepo;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(ReviewViewModel.class)) {
            return (T) new ReviewViewModel(reviewRepo, sessionRepo);
        }
        throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
    }
}
