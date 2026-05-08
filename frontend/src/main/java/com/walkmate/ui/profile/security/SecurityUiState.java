package com.walkmate.ui.profile.security;

public class SecurityUiState {

    private final boolean isLoading;
    private final boolean hasPassword;
    private final boolean hasGoogle;
    private final boolean isSuccess;
    private final String  error;

    public SecurityUiState(boolean isLoading, boolean hasPassword, boolean hasGoogle,
                           boolean isSuccess, String error) {
        this.isLoading   = isLoading;
        this.hasPassword = hasPassword;
        this.hasGoogle   = hasGoogle;
        this.isSuccess   = isSuccess;
        this.error       = error;
    }

    public static SecurityUiState initial() {
        return new SecurityUiState(true, false, false, false, null);
    }

    public boolean isLoading()   { return isLoading; }
    public boolean hasPassword() { return hasPassword; }
    public boolean hasGoogle()   { return hasGoogle; }
    public boolean isSuccess()   { return isSuccess; }
    public String  getError()    { return error; }
}
