package com.walkmate.domain.user;

public class User {
    private final String id;
    private final String fullname;
    private final String email;
    private final AccountStatus accountStatus;
    private final VisibilityMode visibilityMode;

    public User(String id, String fullname, String email,
                AccountStatus accountStatus, VisibilityMode visibilityMode) {
        this.id = id;
        this.fullname = fullname;
        this.email = email;
        this.accountStatus = accountStatus;
        this.visibilityMode = visibilityMode;
    }

    public String getId() {
        return id;
    }

    public String getFullname() {
        return fullname;
    }

    public String getEmail() {
        return email;
    }

    public AccountStatus getAccountStatus() {
        return accountStatus;
    }

    public VisibilityMode getVisibilityMode() {
        return visibilityMode;
    }
}
