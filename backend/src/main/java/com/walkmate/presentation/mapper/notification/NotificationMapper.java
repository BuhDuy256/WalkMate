package com.walkmate.presentation.mapper.notification;

import com.walkmate.domain.notification.Notification;
import com.walkmate.presentation.dto.response.notification.NotificationResponse;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class NotificationMapper {

    public NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(
                notification.getNotificationId(),
                notification.getType().name(),
                notification.getPayload(),
                notification.getStatus().name(),
                fmt(notification.getCreatedAt()),
                fmt(notification.getReadAt())
        );
    }

    private static String fmt(Instant instant) {
        return instant != null ? instant.toString() : null;
    }
}
