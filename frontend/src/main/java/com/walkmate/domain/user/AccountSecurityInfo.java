package com.walkmate.domain.user;

public class AccountSecurityInfo {

    private final boolean hasPassword;
    private final boolean hasGoogle;

    public AccountSecurityInfo(boolean hasPassword, boolean hasGoogle) {
        this.hasPassword = hasPassword;
        this.hasGoogle   = hasGoogle;
    }

    public boolean hasPassword() { return hasPassword; }
    public boolean hasGoogle()   { return hasGoogle; }
}
