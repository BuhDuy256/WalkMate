package com.walkmate.ui.profile.security;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.walkmate.WalkMateApplication;

public class SecurityViewModelFactory implements ViewModelProvider.Factory {

    private final Context context;

    public SecurityViewModelFactory(Context context) {
        this.context = context.getApplicationContext();
    }

    @NonNull
    @Override
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        WalkMateApplication app = (WalkMateApplication) context;
        return modelClass.cast(new SecurityViewModel(app.getUserRepository()));
    }
}
