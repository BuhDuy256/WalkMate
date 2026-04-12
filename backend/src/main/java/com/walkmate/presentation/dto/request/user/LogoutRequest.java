package com.walkmate.presentation.dto.request.user;

import jakarta.validation.constraints.NotBlank;

public record LogoutRequest(
        @NotBlank(message = "deviceId is required")
        String deviceId
) {
}
