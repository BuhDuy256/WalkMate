package com.walkmate.ui.coordination;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.walkmate.data.repository.HotspotRepositoryImpl;

public class CoordinationViewModelFactory implements ViewModelProvider.Factory {

    private final Context appContext;

    public CoordinationViewModelFactory(Context context) {
        this.appContext = context.getApplicationContext();
    }

    @SuppressWarnings("unchecked")
    @NonNull
    @Override
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(CoordinationViewModel.class)) {
            return (T) new CoordinationViewModel(new HotspotRepositoryImpl(appContext));
        }
        throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
    }
}
