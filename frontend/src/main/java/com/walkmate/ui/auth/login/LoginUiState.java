package com.walkmate.ui.auth.login;

public class LoginUiState {
    private final boolean isLoading;
    private final String error;
    private final boolean isSuccess;
    private final boolean forcedLogout;

    public LoginUiState(boolean isLoading, String error, boolean isSuccess, boolean forcedLogout) {
        this.isLoading = isLoading;
        this.error = error;
        this.isSuccess = isSuccess;
        this.forcedLogout = forcedLogout;
    }

    public static LoginUiState initial() {
        return new LoginUiState(false, null, false, false);
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

    public boolean isForcedLogout() {
        return forcedLogout;
    }
}
