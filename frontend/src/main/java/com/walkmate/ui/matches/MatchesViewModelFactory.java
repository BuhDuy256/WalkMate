package com.walkmate.ui.matches;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.walkmate.WalkMateApplication;

public class MatchesViewModelFactory implements ViewModelProvider.Factory {

    private final Application application;

    public MatchesViewModelFactory(Application application) {
        this.application = application;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(MatchesViewModel.class)) {
            WalkMateApplication app = (WalkMateApplication) application;
            return (T) new MatchesViewModel(
                    app.getWalkIntentRepository(),
                    app.getWalkProposalRepository(),
                    app.getWalkSessionRepository());
        }
        throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
    }
}
