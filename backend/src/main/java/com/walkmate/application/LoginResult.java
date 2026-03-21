package com.walkmate.application;

public record LoginResult(
        String accessToken,
        long expiresIn
) {
}
