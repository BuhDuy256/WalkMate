package com.walkmate.application.user;

public record TokenPair(
        String accessToken,
        long accessTokenExpiresIn,
        String refreshToken
) {
}
