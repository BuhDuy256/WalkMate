package com.walkmate.presentation.dto.request.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateFcmTokenRequest(

        @NotBlank(message = "FCM token is required")
        @Size(max = 512, message = "FCM token must be at most 512 characters")
        String fcmToken
) {}
