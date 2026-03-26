package com.walkmate.application.user;

public record LoginResult(
        String accessToken,
        long expiresIn
) {
}
