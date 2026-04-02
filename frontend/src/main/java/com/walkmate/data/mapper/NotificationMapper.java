package com.walkmate.data.mapper;

import com.walkmate.data.datasource.remote.dto.response.notification.NotificationResponse;
import com.walkmate.domain.notification.Notification;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NotificationMapper {

    public static Notification toDomain(NotificationResponse dto) {
        Notification.Type type;
        try {
            type = Notification.Type.valueOf(dto.getType());
        } catch (IllegalArgumentException | NullPointerException e) {
            type = Notification.Type.PROPOSAL_RECEIVED; // safe fallback
        }

        Notification.Status status;
        try {
            status = Notification.Status.valueOf(dto.getStatus());
        } catch (IllegalArgumentException | NullPointerException e) {
            status = Notification.Status.PENDING;
        }

        Map<String, Object> payload = dto.getPayload() != null
                ? new HashMap<>(dto.getPayload())
                : Collections.emptyMap();

        return new Notification(
                dto.getNotificationId(),
                type,
                payload,
                status,
                dto.getCreatedAt(),
                dto.getReadAt()
        );
    }

    public static List<Notification> toDomainList(List<NotificationResponse> dtos) {
        if (dtos == null) return Collections.emptyList();
        List<Notification> result = new ArrayList<>(dtos.size());
        for (NotificationResponse dto : dtos) {
            result.add(toDomain(dto));
        }
        return result;
    }

    private NotificationMapper() {}
}
