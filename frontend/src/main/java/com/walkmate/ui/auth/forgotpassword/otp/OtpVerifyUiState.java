package com.walkmate.ui.auth.forgotpassword.otp;

public class OtpVerifyUiState {
    private final boolean isLoading;
    private final String  error;
    private final String  resetToken; // non-null → verified, proceed to NewPasswordFragment

    public OtpVerifyUiState(boolean isLoading, String error, String resetToken) {
        this.isLoading  = isLoading;
        this.error      = error;
        this.resetToken = resetToken;
    }

    public static OtpVerifyUiState initial() {
        return new OtpVerifyUiState(false, null, null);
    }

    public boolean isLoading()     { return isLoading; }
    public String  getError()      { return error; }
    public String  getResetToken() { return resetToken; }
}
