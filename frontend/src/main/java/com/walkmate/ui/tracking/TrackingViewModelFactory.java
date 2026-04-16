package com.walkmate.ui.tracking;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.walkmate.WalkMateApplication;
import com.walkmate.domain.tracking.TrackingStateRepository;
import com.walkmate.domain.walksession.WalkSessionRepository;

/**
 * Factory that satisfies {@link TrackingViewModel}'s constructor arguments.
 *
 * Usage in {@link TrackingScreenActivity}:
 * <pre>
 *   TrackingViewModelFactory factory =
 *       new TrackingViewModelFactory(getApplication());
 *   viewModel = new ViewModelProvider(this, factory)
 *       .get(TrackingViewModel.class);
 * </pre>
 */
public class TrackingViewModelFactory implements ViewModelProvider.Factory {

    private final Application application;

    public TrackingViewModelFactory(@NonNull Application application) {
        this.application = application;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(TrackingViewModel.class)) {
            WalkMateApplication app = (WalkMateApplication) application;
            WalkSessionRepository sessionRepository = app.getWalkSessionRepository();
            TrackingStateRepository stateRepository = app.getTrackingStateRepository();
            return (T) new TrackingViewModel(application, sessionRepository, stateRepository);
        }
        throw new IllegalArgumentException(
                "TrackingViewModelFactory cannot create: " + modelClass.getName());
    }
}
