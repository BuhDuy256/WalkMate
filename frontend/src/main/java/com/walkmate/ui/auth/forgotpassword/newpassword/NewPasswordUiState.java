package com.walkmate.ui.auth.forgotpassword.newpassword;

public class NewPasswordUiState {
    private final boolean isLoading;
    private final String  error;
    private final boolean isSuccess;

    public NewPasswordUiState(boolean isLoading, String error, boolean isSuccess) {
        this.isLoading = isLoading;
        this.error     = error;
        this.isSuccess = isSuccess;
    }

    public static NewPasswordUiState initial() {
        return new NewPasswordUiState(false, null, false);
    }

    public boolean isLoading()   { return isLoading; }
    public String  getError()    { return error; }
    public boolean isSuccess()   { return isSuccess; }
}
