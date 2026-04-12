package com.walkmate.ui.auth.phone;

import android.content.Context;
import android.os.CountDownTimer;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.walkmate.core.util.UserErrorMessageMapper;
import com.walkmate.domain.shared.DomainCallback;
import com.walkmate.domain.user.UserRepository;

public class PhoneOtpViewModel extends ViewModel {

    private static final int RESEND_COOLDOWN_SECONDS = 60;

    private final UserRepository userRepository;
    private final Context appContext;

    private final MutableLiveData<PhoneOtpUiState> uiState =
            new MutableLiveData<>(PhoneOtpUiState.initial());

    private String lastPhone;
    private CountDownTimer countDownTimer;

    public PhoneOtpViewModel(UserRepository userRepository, Context appContext) {
        this.userRepository = userRepository;
        this.appContext = appContext.getApplicationContext();
    }

    public LiveData<PhoneOtpUiState> getUiState() {
        return uiState;
    }

    public void sendOtp(String phone) {
        if (phone == null || phone.trim().isEmpty()) {
            postError("Phone number is required");
            return;
        }

        lastPhone = phone.trim();
        uiState.setValue(new PhoneOtpUiState(true, false, false, null, 0));

        userRepository.sendOtp(lastPhone, new DomainCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                uiState.postValue(new PhoneOtpUiState(false, true, false, null, RESEND_COOLDOWN_SECONDS));
                startResendCountdown();
            }

            @Override
            public void onError(Exception error) {
                uiState.postValue(buildErrorState(error.getMessage(), false));
            }
        });
    }

    public void verifyOtp(String code) {
        if (lastPhone == null) {
            postError("Session expired. Please restart phone sign-in.");
            return;
        }
        if (code == null || code.trim().length() != 6) {
            postError("Please enter the 6-digit code");
            return;
        }

        PhoneOtpUiState current = uiState.getValue();
        int cooldown = current != null ? current.getResendCooldownSeconds() : 0;
        uiState.setValue(new PhoneOtpUiState(true, true, false, null, cooldown));

        userRepository.verifyOtp(lastPhone, code.trim(), new DomainCallback<String>() {
            @Override
            public void onSuccess(String token) {
                cancelCountdown();
                uiState.postValue(new PhoneOtpUiState(false, true, true, null, 0));
            }

            @Override
            public void onError(Exception error) {
                PhoneOtpUiState cur = uiState.getValue();
                int cd = cur != null ? cur.getResendCooldownSeconds() : 0;
                UserErrorMessageMapper.ErrorResult result =
                        UserErrorMessageMapper.map(error.getMessage());
                String message = appContext.getString(result.messageResId);
                uiState.postValue(new PhoneOtpUiState(false, true, false, message, cd));
            }
        });
    }

    public void resendOtp() {
        if (lastPhone != null) {
            sendOtp(lastPhone);
        }
    }

    public void consumeError() {
        PhoneOtpUiState current = uiState.getValue();
        if (current != null) {
            uiState.setValue(new PhoneOtpUiState(
                    current.isLoading(), current.isOtpSent(), current.isSuccess(),
                    null, current.getResendCooldownSeconds()));
        }
    }

    // ── Countdown timer ───────────────────────────────────────────────────────

    private void startResendCountdown() {
        cancelCountdown();
        countDownTimer = new CountDownTimer(RESEND_COOLDOWN_SECONDS * 1000L, 1000L) {
            @Override
            public void onTick(long millisUntilFinished) {
                int seconds = (int) (millisUntilFinished / 1000);
                PhoneOtpUiState cur = uiState.getValue();
                if (cur != null) {
                    uiState.postValue(new PhoneOtpUiState(
                            cur.isLoading(), cur.isOtpSent(), cur.isSuccess(),
                            cur.getError(), seconds));
                }
            }

            @Override
            public void onFinish() {
                PhoneOtpUiState cur = uiState.getValue();
                if (cur != null) {
                    uiState.postValue(new PhoneOtpUiState(
                            cur.isLoading(), cur.isOtpSent(), cur.isSuccess(),
                            cur.getError(), 0));
                }
            }
        }.start();
    }

    private void cancelCountdown() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
            countDownTimer = null;
        }
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        cancelCountdown();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void postError(String message) {
        uiState.setValue(new PhoneOtpUiState(false, false, false, message, 0));
    }

    private PhoneOtpUiState buildErrorState(String errorCode, boolean otpSent) {
        UserErrorMessageMapper.ErrorResult result = UserErrorMessageMapper.map(errorCode);
        String message = appContext.getString(result.messageResId);
        return new PhoneOtpUiState(false, otpSent, false, message, 0);
    }
}
