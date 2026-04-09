package com.walkmate.ui.auth.phone;

public class PhoneOtpUiState {

    private final boolean isLoading;
    private final boolean otpSent;
    private final boolean isSuccess;
    private final String error;
    private final int resendCooldownSeconds;

    public PhoneOtpUiState(boolean isLoading, boolean otpSent, boolean isSuccess,
                           String error, int resendCooldownSeconds) {
        this.isLoading = isLoading;
        this.otpSent = otpSent;
        this.isSuccess = isSuccess;
        this.error = error;
        this.resendCooldownSeconds = resendCooldownSeconds;
    }

    public static PhoneOtpUiState initial() {
        return new PhoneOtpUiState(false, false, false, null, 0);
    }

    public boolean isLoading()             { return isLoading; }
    public boolean isOtpSent()             { return otpSent; }
    public boolean isSuccess()             { return isSuccess; }
    public String getError()               { return error; }
    public int getResendCooldownSeconds()  { return resendCooldownSeconds; }
}
