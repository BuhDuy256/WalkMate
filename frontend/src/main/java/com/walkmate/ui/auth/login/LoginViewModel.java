package com.walkmate.ui.auth.login;

import android.content.Context;
import android.util.Patterns;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.walkmate.core.util.UserErrorMessageMapper;
import com.walkmate.domain.shared.DomainCallback;
import com.walkmate.domain.user.UserRepository;

public class LoginViewModel extends ViewModel {

    private final UserRepository userRepository;
    private final Context appContext;

    private final MutableLiveData<LoginUiState> uiState = new MutableLiveData<>(LoginUiState.initial());

    public LoginViewModel(UserRepository userRepository, Context appContext) {
        this.userRepository = userRepository;
        this.appContext = appContext.getApplicationContext();
    }

    public LiveData<LoginUiState> getUiState() {
        return uiState;
    }

    public void login(String email, String password) {
        if (email == null || email.trim().isEmpty()) {
            uiState.setValue(new LoginUiState(false, "Email is required", false, false));
            return;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) {
            uiState.setValue(new LoginUiState(false, "Please enter a valid email address", false, false));
            return;
        }
        if (password == null || password.trim().isEmpty()) {
            uiState.setValue(new LoginUiState(false, "Password is required", false, false));
            return;
        }

        uiState.setValue(new LoginUiState(true, null, false, false));

        String deviceId = userRepository.getOrGenerateDeviceId();
        userRepository.login(email.trim(), password.trim(), deviceId, new DomainCallback<String>() {
            @Override
            public void onSuccess(String token) {
                uiState.postValue(new LoginUiState(false, null, true, false));
            }

            @Override
            public void onError(Exception error) {
                uiState.postValue(buildErrorState(error.getMessage()));
            }
        });
    }

    public void loginWithGoogle(String googleIdToken) {
        if (googleIdToken == null || googleIdToken.trim().isEmpty()) {
            uiState.setValue(new LoginUiState(false, "Google Sign-In failed. Please try again.", false, false));
            return;
        }

        uiState.setValue(new LoginUiState(true, null, false, false));

        String deviceId = userRepository.getOrGenerateDeviceId();
        userRepository.loginWithGoogle(googleIdToken, deviceId, new DomainCallback<String>() {
            @Override
            public void onSuccess(String token) {
                uiState.postValue(new LoginUiState(false, null, true, false));
            }

            @Override
            public void onError(Exception error) {
                uiState.postValue(buildErrorState(error.getMessage()));
            }
        });
    }

    public void consumeError() {
        LoginUiState current = uiState.getValue();
        if (current != null) {
            uiState.setValue(new LoginUiState(current.isLoading(), null, current.isSuccess(), false));
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private LoginUiState buildErrorState(String errorCode) {
        UserErrorMessageMapper.ErrorResult result = UserErrorMessageMapper.map(errorCode);
        String message = appContext.getString(result.messageResId);

        if (result.actionType == UserErrorMessageMapper.ActionType.FORCE_LOGOUT) {
            return new LoginUiState(false, message, false, true);
        }
        return new LoginUiState(false, message, false, false);
    }
}
