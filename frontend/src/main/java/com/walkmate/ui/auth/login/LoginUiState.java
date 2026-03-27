package com.walkmate.ui.auth.login;

public class LoginUiState {
    private final boolean isLoading;
    private final String error;
    private final boolean isSuccess;

    public LoginUiState(boolean isLoading, String error, boolean isSuccess) {
        this.isLoading = isLoading;
        this.error = error;
        this.isSuccess = isSuccess;
    }

    public static LoginUiState initial() {
        return new LoginUiState(false, null, false);
    }

    public boolean isLoading() {
        return isLoading;
    }

    public String getError() {
        return error;
    }

    public boolean isSuccess() {
        return isSuccess;
    }
}
