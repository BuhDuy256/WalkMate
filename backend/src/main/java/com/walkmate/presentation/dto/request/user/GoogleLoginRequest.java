package com.walkmate.presentation.dto.request.user;

import jakarta.validation.constraints.NotBlank;

public record GoogleLoginRequest(
        @NotBlank(message = "Firebase ID token is required")
        String idToken
) {
}
