package com.walkmate.ui.auth.forgotpassword.otp;

import android.content.Context;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.walkmate.core.util.UserErrorMessageMapper;
import com.walkmate.domain.shared.DomainCallback;
import com.walkmate.domain.user.UserRepository;

public class OtpVerifyViewModel extends ViewModel {

    private final UserRepository userRepository;
    private final Context        appContext;

    private final MutableLiveData<OtpVerifyUiState> uiState =
            new MutableLiveData<>(OtpVerifyUiState.initial());

    public OtpVerifyViewModel(UserRepository userRepository, Context appContext) {
        this.userRepository = userRepository;
        this.appContext     = appContext.getApplicationContext();
    }

    public LiveData<OtpVerifyUiState> getUiState() { return uiState; }

    public void verifyOtp(String email, String otp) {
        if (otp == null || otp.length() != 6) {
            uiState.setValue(new OtpVerifyUiState(false, "Please enter the 6-digit code", null));
            return;
        }

        uiState.setValue(new OtpVerifyUiState(true, null, null));

        userRepository.verifyPasswordReset(email, otp, new DomainCallback<String>() {
            @Override public void onSuccess(String resetToken) {
                uiState.postValue(new OtpVerifyUiState(false, null, resetToken));
            }
            @Override public void onError(Exception error) {
                uiState.postValue(buildError(error.getMessage()));
            }
        });
    }

    public void resendOtp(String email) {
        userRepository.requestPasswordReset(email, new DomainCallback<Void>() {
            @Override public void onSuccess(Void result) {}
            @Override public void onError(Exception error) {}
        });
    }

    public void consumeError() {
        OtpVerifyUiState s = uiState.getValue();
        if (s != null) uiState.setValue(new OtpVerifyUiState(s.isLoading(), null, s.getResetToken()));
    }

    private OtpVerifyUiState buildError(String errorCode) {
        UserErrorMessageMapper.ErrorResult r = UserErrorMessageMapper.map(errorCode);
        return new OtpVerifyUiState(false, appContext.getString(r.messageResId), null);
    }
}
