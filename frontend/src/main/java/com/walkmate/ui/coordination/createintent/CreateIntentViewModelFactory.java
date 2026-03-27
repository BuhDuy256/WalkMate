package com.walkmate.ui.coordination.createintent;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.walkmate.data.repository.WalkIntentRepositoryImpl;

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
            return (T) new CreateIntentViewModel(new WalkIntentRepositoryImpl(appContext));
        }
        throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
    }
}
