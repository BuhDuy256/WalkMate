package com.walkmate.ui.auth.forgotpassword.email;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.walkmate.WalkMateApplication;
import com.walkmate.domain.user.UserRepository;

public class EmailInputViewModelFactory implements ViewModelProvider.Factory {

    private final UserRepository userRepository;
    private final Context        appContext;

    public EmailInputViewModelFactory(Context context) {
        this.appContext     = context.getApplicationContext();
        this.userRepository = ((WalkMateApplication) appContext).getUserRepository();
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(EmailInputViewModel.class)) {
            return (T) new EmailInputViewModel(userRepository, appContext);
        }
        throw new IllegalArgumentException("Unknown ViewModel class");
    }
}
