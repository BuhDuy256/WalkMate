package com.walkmate.presentation.dto.response.user;

import java.util.UUID;

public record RegisterUserResponse(
        UUID userId,
        String email
) {
}
