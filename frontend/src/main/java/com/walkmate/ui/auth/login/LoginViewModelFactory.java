package com.walkmate.ui.auth.login;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.walkmate.WalkMateApplication;
import com.walkmate.domain.user.UserRepository;

public class LoginViewModelFactory implements ViewModelProvider.Factory {

    private final UserRepository userRepository;

    public LoginViewModelFactory(Context context) {
        this.userRepository = ((WalkMateApplication) context.getApplicationContext()).getUserRepository();
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(LoginViewModel.class)) {
            return (T) new LoginViewModel(userRepository);
        }
        throw new IllegalArgumentException("Unknown ViewModel class");
    }
}
