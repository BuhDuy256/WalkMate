package com.walkmate.domain.notification;

import java.util.List;
import java.util.Optional;

public interface NotificationRepository {

    /** Persists a new notification or updates an existing one (upsert on notification_id). */
    Notification save(Notification notification);

    Optional<Notification> findById(String notificationId);

    /** Returns all notifications for a user, ordered newest-first. */
    List<Notification> findByUserId(String userId);
}
