package com.walkmate.ui.login;

import android.util.Patterns;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.walkmate.domain.shared.DomainCallback;
import com.walkmate.domain.user.UserRepository;

public class LoginViewModel extends ViewModel {

    private final UserRepository userRepository;
    
    // Unidirectional Data Flow state
    private final MutableLiveData<LoginUiState> uiState = new MutableLiveData<>(LoginUiState.initial());
    
    public LoginViewModel(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public LiveData<LoginUiState> getUiState() {
        return uiState;
    }

    public void login(String email, String password) {
        if (email == null || email.trim().isEmpty()) {
            uiState.setValue(new LoginUiState(false, "Email is required", false));
            return;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) {
            uiState.setValue(new LoginUiState(false, "Please enter a valid email address", false));
            return;
        }
        if (password == null || password.trim().isEmpty()) {
            uiState.setValue(new LoginUiState(false, "Password is required", false));
            return;
        }

        uiState.setValue(new LoginUiState(true, null, false));

        userRepository.login(email.trim(), password.trim(), new DomainCallback<String>() {
            @Override
            public void onSuccess(String token) {
                // Background thread requires postValue
                uiState.postValue(new LoginUiState(false, null, true));
            }

            @Override
            public void onError(Exception error) {
                uiState.postValue(new LoginUiState(false, error.getMessage(), false));
            }
        });
    }

    public void consumeError() {
        uiState.setValue(new LoginUiState(uiState.getValue().isLoading(), null, uiState.getValue().isSuccess()));
    }
}
