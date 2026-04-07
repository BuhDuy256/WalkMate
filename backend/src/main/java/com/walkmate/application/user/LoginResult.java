package com.walkmate.application.user;

public record LoginResult(
        String accessToken,
        long   expiresIn,
        String refreshToken,
        long   refreshTokenExpiresIn
) {
}
