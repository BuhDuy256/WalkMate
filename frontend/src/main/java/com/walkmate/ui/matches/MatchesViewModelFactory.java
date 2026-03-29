package com.walkmate.ui.matches;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.walkmate.data.repository.WalkIntentRepositoryImpl;
import com.walkmate.data.repository.WalkProposalRepositoryImpl;
import com.walkmate.data.repository.WalkSessionRepositoryImpl;

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
            return (T) new MatchesViewModel(
                    new WalkIntentRepositoryImpl(application),
                    new WalkProposalRepositoryImpl(),
                    new WalkSessionRepositoryImpl());
        }
        throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
    }
}
