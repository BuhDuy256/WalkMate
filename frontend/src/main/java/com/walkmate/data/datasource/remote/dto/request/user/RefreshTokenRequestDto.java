package com.walkmate.data.datasource.remote.dto.request.user;

public class RefreshTokenRequestDto {
    private final String refreshToken;

    public RefreshTokenRequestDto(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }
}
