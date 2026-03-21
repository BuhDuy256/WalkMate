package com.walkmate.application;

public record TokenPair(
        String accessToken,
        long accessTokenExpiresIn,
        String refreshToken
) {
}
