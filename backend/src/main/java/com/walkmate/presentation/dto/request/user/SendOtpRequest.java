package com.walkmate.presentation.dto.request.user;

import jakarta.validation.constraints.NotBlank;

public record SendOtpRequest(
        @NotBlank(message = "phone is required")
        String phone
) {
}
