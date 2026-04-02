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
}
