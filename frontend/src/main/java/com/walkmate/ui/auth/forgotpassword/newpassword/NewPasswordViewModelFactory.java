package com.walkmate.ui.auth.forgotpassword.newpassword;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.walkmate.WalkMateApplication;
import com.walkmate.domain.user.UserRepository;

public class NewPasswordViewModelFactory implements ViewModelProvider.Factory {

    private final UserRepository userRepository;
    private final Context        appContext;

    public NewPasswordViewModelFactory(Context context) {
        this.appContext     = context.getApplicationContext();
        this.userRepository = ((WalkMateApplication) appContext).getUserRepository();
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(NewPasswordViewModel.class)) {
            return (T) new NewPasswordViewModel(userRepository, appContext);
        }
        throw new IllegalArgumentException("Unknown ViewModel class");
    }
}
