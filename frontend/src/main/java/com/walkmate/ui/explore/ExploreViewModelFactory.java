package com.walkmate.ui.explore;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.lifecycle.AbstractSavedStateViewModelFactory;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;
import androidx.savedstate.SavedStateRegistryOwner;

import com.walkmate.WalkMateApplication;

public class ExploreViewModelFactory extends AbstractSavedStateViewModelFactory {

    private final Context appContext;

    public ExploreViewModelFactory(
            @NonNull SavedStateRegistryOwner owner,
            @NonNull Bundle defaultArgs,
            @NonNull Context context) {
        super(owner, defaultArgs);
        this.appContext = context.getApplicationContext();
    }

    @SuppressWarnings("unchecked")
    @NonNull
    @Override
    protected <T extends ViewModel> T create(
            @NonNull String key,
            @NonNull Class<T> modelClass,
            @NonNull SavedStateHandle handle) {
        if (modelClass.isAssignableFrom(ExploreViewModel.class)) {
            WalkMateApplication app = (WalkMateApplication) appContext;
            return (T) new ExploreViewModel(
                    app.getHotspotRepository(),
                    app.getWalkIntentRepository(),
                    handle);
        }
        throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
    }
}
