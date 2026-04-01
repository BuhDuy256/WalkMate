package com.walkmate.domain.notification;

public enum NotificationStatus {
    /** Persisted but not yet delivered to any channel. */
    PENDING,
    /** Delivered (e.g. via FCM — reserved for future push integration). */
    SENT,
    /** The user has explicitly read / dismissed the notification. */
    READ
}
