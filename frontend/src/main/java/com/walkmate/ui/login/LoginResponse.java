package com.walkmate.ui.login;

public class LoginResponse {

    private String accessToken;
    private String tokenType;
    private long expiresIn;

    public String getAccessToken() {
        return accessToken;
    }

    public String getTokenType() {
        return tokenType;
    }

    public long getExpiresIn() {
        return expiresIn;
    }
}
