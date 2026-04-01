package com.walkmate.domain.shared;

import com.walkmate.domain.notification.Notification;

/**
 * Domain-layer port for dispatching notifications.
 *
 * <p>Application services call this interface to emit lifecycle notifications
 * without knowing whether they are delivered via database persistence, FCM push,
 * WebSocket, or any future channel. The infrastructure adapter
 * ({@code NotificationPublisherImpl}) decides the actual delivery mechanism.</p>
 *
 * <p>Implementations MUST NOT throw — failures are logged and swallowed so that
 * a notification-delivery hiccup never rolls back a business transaction.</p>
 */
public interface NotificationPublisher {
    void publish(Notification notification);
}
