package com.walkmate.application.notification;

/**
 * Application-layer port for sending real-time push notifications to a specific
 * device via its FCM registration token.
 *
 * <p>This interface is <em>distinct</em> from
 * {@link com.walkmate.domain.shared.NotificationPublisher}, which persists
 * {@code Notification} entities to the database for the in-app feed.
 * {@code PushNotificationProvider} is responsible for the <em>out-of-band</em>
 * push channel: it sends a data-only payload directly to a device so the
 * Android client can react in real-time even when the app is backgrounded.</p>
 *
 * <p>Contract for all implementations:</p>
 * <ul>
 *   <li>MUST use data-only payloads (no {@code notification} block) so
 *       {@code onMessageReceived()} is called regardless of foreground/background state.</li>
 *   <li>MUST NOT throw — delivery failures are logged and swallowed so that a push
 *       failure never rolls back a business transaction.</li>
 *   <li>MUST NOT import or reference any Firebase/vendor types — the infrastructure
 *       implementation owns those details.</li>
 * </ul>
 */
import com.walkmate.domain.notification.NotificationType;

import java.util.Map;

public interface PushNotificationProvider {

    /**
     * Sends a {@code MATCH_FOUND} push to the device identified by {@code fcmToken}.
     *
     * <p>The Android client (WalkMateFcmService) reads the payload and posts a
     * {@code AppEvent(MATCH_FOUND)} to {@code AppEventBus}, which navigates the user
     * to the Matches → Proposal sub-tab.</p>
     *
     * @param fcmToken   the recipient device's FCM registration token
     * @param intentId   the WalkIntent UUID that triggered the match (recipient's intent)
     * @param proposalId the MatchProposal UUID that was created
     */
    void sendMatchFound(String fcmToken, String intentId, String proposalId);

    /**
     * Sends a generic data-only push notification to a device.
     *
     * <p>This is the primary dispatch method used by {@code NotificationPublisherImpl}
     * for all lifecycle events. The existing {@code sendMatchFound()} remains for
     * backwards compatibility but new notification types route through here.</p>
     *
     * <p>Return value contract:</p>
     * <ul>
     *   <li>{@code true} — message delivered, or failure was transient (network, quota,
     *       internal error). The token may still be valid; do NOT clear it.</li>
     *   <li>{@code false} — FCM permanently rejected the token ({@code UNREGISTERED} or
     *       {@code INVALID_ARGUMENT}). The caller MUST clear the token from the DB to
     *       prevent accumulating stale-token errors.</li>
     * </ul>
     *
     * @param fcmToken FCM registration token of the target device
     * @param type     the notification type — mapped to the {@code "type"} data field
     * @param payload  arbitrary key/value data included in the FCM data payload
     * @return {@code false} if the token is definitively dead and must be cleared
     */
    boolean sendPush(String fcmToken, NotificationType type, Map<String, Object> payload);
}
