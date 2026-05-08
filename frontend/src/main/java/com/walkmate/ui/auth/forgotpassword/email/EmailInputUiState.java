package com.walkmate.ui.auth.forgotpassword.email;

public class EmailInputUiState {
    private final boolean isLoading;
    private final String  error;
    private final boolean otpSent;

    public EmailInputUiState(boolean isLoading, String error, boolean otpSent) {
        this.isLoading = isLoading;
        this.error     = error;
        this.otpSent   = otpSent;
    }

    public static EmailInputUiState initial() {
        return new EmailInputUiState(false, null, false);
    }

    public boolean isLoading() { return isLoading; }
    public String  getError()  { return error; }
    public boolean isOtpSent() { return otpSent; }
}
