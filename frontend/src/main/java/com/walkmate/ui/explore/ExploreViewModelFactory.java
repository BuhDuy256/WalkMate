package com.walkmate.ui.explore;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.walkmate.data.repository.HotspotRepositoryImpl;
import com.walkmate.data.repository.WalkIntentRepositoryImpl;

public class ExploreViewModelFactory implements ViewModelProvider.Factory {

    private final Context appContext;

    public ExploreViewModelFactory(Context context) {
        this.appContext = context.getApplicationContext();
    }

    @SuppressWarnings("unchecked")
    @NonNull
    @Override
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(ExploreViewModel.class)) {
            return (T) new ExploreViewModel(
                    new HotspotRepositoryImpl(appContext),
                    new WalkIntentRepositoryImpl(appContext));
        }
        throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
    }
}
