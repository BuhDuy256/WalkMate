package com.walkmate.presentation.dto.response.user;

public record LoginUserResponse(
        String accessToken,
        String tokenType,
        long expiresIn
) {
}
