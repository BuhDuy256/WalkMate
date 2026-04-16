package com.walkmate.data.datasource.remote.dto.response.user;

public class LoginResponseDto {
    private String accessToken;
    private String tokenType;
    private long expiresIn;
    private String refreshToken;
    private long refreshTokenExpiresIn;

    public String getAccessToken() {
        return accessToken;
    }

    public String getTokenType() {
        return tokenType;
    }

    public long getExpiresIn() {
        return expiresIn;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public long getRefreshTokenExpiresIn() {
        return refreshTokenExpiresIn;
    }
}
