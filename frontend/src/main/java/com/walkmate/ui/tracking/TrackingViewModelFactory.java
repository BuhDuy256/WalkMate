package com.walkmate.ui.tracking;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

/**
 * Factory that satisfies {@link TrackingViewModel}'s {@link Application} constructor
 * argument, which the default no-arg factory cannot provide.
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
            return (T) new TrackingViewModel(application);
        }
        throw new IllegalArgumentException(
                "TrackingViewModelFactory cannot create: " + modelClass.getName());
    }
}
