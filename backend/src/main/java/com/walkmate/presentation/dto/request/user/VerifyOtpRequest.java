package com.walkmate.presentation.dto.request.user;

import jakarta.validation.constraints.NotBlank;

public record VerifyOtpRequest(
        @NotBlank(message = "phone is required")
        String phone,

        @NotBlank(message = "code is required")
        String code,

        @NotBlank(message = "deviceId is required")
        String deviceId
) {
}
