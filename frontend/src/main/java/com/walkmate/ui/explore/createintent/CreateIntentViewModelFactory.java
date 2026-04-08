package com.walkmate.ui.explore.createintent;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.walkmate.WalkMateApplication;

public class CreateIntentViewModelFactory implements ViewModelProvider.Factory {

    private final Context appContext;

    public CreateIntentViewModelFactory(Context context) {
        this.appContext = context.getApplicationContext();
    }

    @SuppressWarnings("unchecked")
    @NonNull
    @Override
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(CreateIntentViewModel.class)) {
            WalkMateApplication app = (WalkMateApplication) appContext;
            return (T) new CreateIntentViewModel(app.getWalkIntentRepository());
        }
        throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
    }
}
