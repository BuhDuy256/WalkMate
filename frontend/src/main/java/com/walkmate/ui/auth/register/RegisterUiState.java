package com.walkmate.ui.auth.register;

public class RegisterUiState {
    private final boolean isLoading;
    private final boolean isSuccess;
    private final String error;
    private final String fullNameError;
    private final String emailError;
    private final String passwordError;
    private final String confirmPasswordError;

    public RegisterUiState(boolean isLoading, boolean isSuccess, String error,
            String fullNameError, String emailError, String passwordError,
            String confirmPasswordError) {
        this.isLoading = isLoading;
        this.isSuccess = isSuccess;
        this.error = error;
        this.fullNameError = fullNameError;
        this.emailError = emailError;
        this.passwordError = passwordError;
        this.confirmPasswordError = confirmPasswordError;
    }

    public static RegisterUiState initial() {
        return new RegisterUiState(false, false, null, null, null, null, null);
    }

    public boolean isLoading() {
        return isLoading;
    }

    public boolean isSuccess() {
        return isSuccess;
    }

    public String getError() {
        return error;
    }

    public String getFullNameError() {
        return fullNameError;
    }

    public String getEmailError() {
        return emailError;
    }

    public String getPasswordError() {
        return passwordError;
    }

    public String getConfirmPasswordError() {
        return confirmPasswordError;
    }
}
