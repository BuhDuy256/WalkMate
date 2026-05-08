package com.walkmate.ui.auth.forgotpassword.email;

import android.content.Context;
import android.util.Patterns;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.walkmate.core.util.UserErrorMessageMapper;
import com.walkmate.domain.shared.DomainCallback;
import com.walkmate.domain.user.UserRepository;

public class EmailInputViewModel extends ViewModel {

    private final UserRepository userRepository;
    private final Context        appContext;

    private final MutableLiveData<EmailInputUiState> uiState =
            new MutableLiveData<>(EmailInputUiState.initial());

    public EmailInputViewModel(UserRepository userRepository, Context appContext) {
        this.userRepository = userRepository;
        this.appContext     = appContext.getApplicationContext();
    }

    public LiveData<EmailInputUiState> getUiState() { return uiState; }

    public void sendOtp(String email) {
        if (email == null || email.trim().isEmpty()) {
            uiState.setValue(new EmailInputUiState(false, "Email is required", false));
            return;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) {
            uiState.setValue(new EmailInputUiState(false, "Please enter a valid email address", false));
            return;
        }

        uiState.setValue(new EmailInputUiState(true, null, false));

        userRepository.requestPasswordReset(email.trim(), new DomainCallback<Void>() {
            @Override public void onSuccess(Void result) {
                uiState.postValue(new EmailInputUiState(false, null, true));
            }
            @Override public void onError(Exception error) {
                uiState.postValue(buildError(error.getMessage()));
            }
        });
    }

    public void consumeError() {
        EmailInputUiState s = uiState.getValue();
        if (s != null) uiState.setValue(new EmailInputUiState(s.isLoading(), null, s.isOtpSent()));
    }

    public void consumeOtpSent() {
        EmailInputUiState s = uiState.getValue();
        if (s != null) uiState.setValue(new EmailInputUiState(s.isLoading(), s.getError(), false));
    }

    private EmailInputUiState buildError(String errorCode) {
        UserErrorMessageMapper.ErrorResult r = UserErrorMessageMapper.map(errorCode);
        return new EmailInputUiState(false, appContext.getString(r.messageResId), false);
    }
}
