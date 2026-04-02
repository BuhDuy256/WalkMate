package com.walkmate.infrastructure.notification;

import com.walkmate.domain.notification.Notification;
import com.walkmate.domain.notification.NotificationRepository;
import com.walkmate.domain.shared.NotificationPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Pull-based implementation of {@link NotificationPublisher}.
 *
 * <p>Persists every notification to the {@code notification} table so the
 * client can poll {@code GET /api/v1/notifications}. Push delivery via FCM
 * is deferred to a later phase; this implementation is the single place to
 * add it when the time comes.</p>
 *
 * <p>Failures are caught and logged — a broken notification must never
 * roll back a business transaction.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationPublisherImpl implements NotificationPublisher {

    private final NotificationRepository notificationRepository;

    @Override
    public void publish(Notification notification) {
        try {
            notificationRepository.save(notification);
            log.debug("Notification persisted: type={} userId={}",
                    notification.getType(), notification.getUserId());
        } catch (Exception ex) {
            log.error("Failed to persist notification type={} userId={}: {}",
                    notification.getType(), notification.getUserId(), ex.getMessage(), ex);
        }
    }
}
