package com.walkmate.ui.auth.register;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.walkmate.data.repository.UserRepositoryImpl;
import com.walkmate.domain.user.UserRepository;

public class RegisterViewModelFactory implements ViewModelProvider.Factory {

    private final UserRepository userRepository;
    private final Context appContext;

    public RegisterViewModelFactory(Context context) {
        this.appContext = context.getApplicationContext();
        this.userRepository = new UserRepositoryImpl(appContext);
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(RegisterViewModel.class)) {
            return (T) new RegisterViewModel(userRepository, appContext);
        }
        throw new IllegalArgumentException("Unknown ViewModel class");
    }
}
