package com.walkmate.presentation.dto.response.notification;

import java.util.Map;

public record NotificationResponse(
        String notificationId,
        String type,
        Map<String, Object> payload,
        String status,
        String createdAt,
        String readAt       // null when status != READ
) {}
