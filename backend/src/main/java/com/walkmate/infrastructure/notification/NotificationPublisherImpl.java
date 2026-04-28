package com.walkmate.infrastructure.notification;

import com.walkmate.application.notification.PushNotificationProvider;
import com.walkmate.domain.notification.Notification;
import com.walkmate.domain.notification.NotificationRepository;
import com.walkmate.domain.shared.NotificationPublisher;
import com.walkmate.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Dual-channel implementation of {@link NotificationPublisher}.
 *
 * <p>On every {@link #publish(Notification)} call, two channels are dispatched independently:</p>
 * <ol>
 *   <li><b>Channel 1 — DB persist</b>: writes to the {@code notification} table for the
 *       in-app feed. Always attempted first. An exception here is caught and logged
 *       but does NOT block Channel 2.</li>
 *   <li><b>Channel 2 — FCM push</b>: looks up the user's FCM token via {@link UserRepository}
 *       and calls {@link PushNotificationProvider#sendPush}. Best-effort — exceptions
 *       are caught independently and never affect Channel 1 or the calling transaction.</li>
 * </ol>
 *
 * <p>Firebase Admin SDK types are confined to the infrastructure layer and do not
 * leak upward through this class.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationPublisherImpl implements NotificationPublisher {

    private final NotificationRepository    notificationRepository;
    private final UserRepository            userRepository;
    private final PushNotificationProvider  pushNotificationProvider;

    @Override
    public void publish(Notification notification) {
        // Channel 1: persist to DB (in-app notification feed — always attempted)
        try {
            notificationRepository.save(notification);
            log.debug("Notification persisted: type={} userId={}",
                    notification.getType(), notification.getUserId());
        } catch (Exception ex) {
            log.error("Failed to persist notification type={} userId={}: {}",
                    notification.getType(), notification.getUserId(), ex.getMessage(), ex);
        }

        // Channel 2: FCM push (real-time device delivery — best-effort)
        // Token lookup is infra-layer only; no Firebase type leaks upward.
        try {
            userRepository.findById(notification.getUserId()).ifPresent(user -> {
                String token = user.getFcmToken();
                if (token != null && !token.isBlank()) {
                    boolean tokenStillValid = pushNotificationProvider.sendPush(
                            token,
                            notification.getType(),
                            notification.getPayload()
                    );
                    if (!tokenStillValid) {
                        // FCM permanently rejected the token (UNREGISTERED / INVALID_ARGUMENT).
                        // Clear it so subsequent publishes skip the FCM dispatch instead of
                        // accumulating dead-token errors that degrade Firebase project reputation.
                        userRepository.updateFcmToken(
                                UUID.fromString(notification.getUserId()), null);
                        log.info("Cleared stale FCM token for userId={}", notification.getUserId());
                    }
                }
            });
        } catch (Exception ex) {
            // A failure to resolve the FCM token must never block DB persistence.
            log.error("FCM dispatch failed for notification type={} userId={}: {}",
                    notification.getType(), notification.getUserId(), ex.getMessage(), ex);
        }
    }
}
