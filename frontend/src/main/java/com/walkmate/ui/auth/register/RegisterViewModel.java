package com.walkmate.ui.auth.register;

import android.content.Context;
import android.util.Patterns;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.walkmate.core.util.UserErrorMessageMapper;
import com.walkmate.domain.shared.DomainCallback;
import com.walkmate.domain.user.UserRepository;

import java.util.regex.Pattern;

public class RegisterViewModel extends ViewModel {

    private final UserRepository userRepository;
    private final Context appContext;
    private final MutableLiveData<RegisterUiState> uiState = new MutableLiveData<>(RegisterUiState.initial());

    public RegisterViewModel(UserRepository userRepository, Context appContext) {
        this.userRepository = userRepository;
        this.appContext = appContext.getApplicationContext();
    }

    public LiveData<RegisterUiState> getUiState() {
        return uiState;
    }

    public void register(String fullName, String email, String password) {
        String fNameError = null;
        String eError = null;
        String pError = null;

        if (fullName == null || fullName.trim().isEmpty()) {
            fNameError = "Full Name is required";
        } else if (fullName.trim().length() < 5) {
            fNameError = "Name must be at least 5 characters";
        }

        if (email == null || email.trim().isEmpty()) {
            eError = "Email is required";
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) {
            eError = "Please enter a valid email address";
        }

        if (password == null || password.trim().isEmpty()) {
            pError = "Password is required";
        } else if (!isValidPassword(password.trim())) {
            pError = "Password must be at least 8 characters, include uppercase, number, and special character";
        }

        if (fNameError != null || eError != null || pError != null) {
            uiState.setValue(new RegisterUiState(false, false, null, fNameError, eError, pError));
            return;
        }

        uiState.setValue(new RegisterUiState(true, false, null, null, null, null));

        String deviceId = userRepository.getOrGenerateDeviceId();
        userRepository.register(fullName.trim(), email.trim(), password.trim(), deviceId,
                new DomainCallback<String>() {
                    @Override
                    public void onSuccess(String token) {
                        uiState.postValue(new RegisterUiState(false, true, null, null, null, null));
                    }

                    @Override
                    public void onError(Exception error) {
                        UserErrorMessageMapper.ErrorResult result =
                                UserErrorMessageMapper.map(error.getMessage());
                        String message = appContext.getString(result.messageResId);
                        uiState.postValue(
                                new RegisterUiState(false, false, message, null, null, null));
                    }
                });
    }

    private boolean isValidPassword(String password) {
        String passwordPattern = "^(?=.*[0-9])(?=.*[A-Z])(?=.*[@#$%^&+=!])(?=\\S+$).{8,}$";
        return Pattern.compile(passwordPattern).matcher(password).matches();
    }

    public void consumeError() {
        RegisterUiState current = uiState.getValue();
        if (current != null) {
            uiState.setValue(new RegisterUiState(current.isLoading(), current.isSuccess(), null,
                    current.getFullNameError(), current.getEmailError(), current.getPasswordError()));
        }
    }
}
