package com.walkmate.presentation.dto.request.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record RequestPasswordResetRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Email is invalid")
        String email
) {}
