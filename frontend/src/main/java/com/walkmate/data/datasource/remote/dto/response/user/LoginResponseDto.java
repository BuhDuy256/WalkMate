package com.walkmate.data.datasource.remote.dto.response.user;

public class LoginResponseDto {
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
