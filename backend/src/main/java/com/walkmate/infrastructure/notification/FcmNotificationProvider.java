package com.walkmate.infrastructure.notification;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.walkmate.application.notification.PushNotificationProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Firebase Cloud Messaging implementation of {@link PushNotificationProvider}.
 *
 * <p>Dependency boundary — this class is the <em>only</em> place in the codebase
 * that imports Firebase Admin SDK types. No Firebase type leaks upward into the
 * {@code application} or {@code domain} layers.</p>
 *
 * <p>All messages use a <b>data-only</b> payload (no {@code notification} block).
 * This guarantees that the Android {@code FirebaseMessagingService.onMessageReceived()}
 * callback is invoked regardless of whether the app is in the foreground or background,
 * giving the client full control over how the event is surfaced to the user.</p>
 *
 * <p>Failures are caught and logged — a broken FCM connection must never roll back
 * a business transaction. Callers do not need try-catch blocks.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FcmNotificationProvider implements PushNotificationProvider {

    private final FirebaseMessaging firebaseMessaging;

    /**
     * Sends a {@code MATCH_FOUND} data message to the specified device token.
     *
     * <p>Android payload received by {@code WalkMateFcmService.onMessageReceived()}:</p>
     * <pre>
     * {
     *   "type":       "MATCH_FOUND",
     *   "intentId":   "&lt;uuid&gt;",
     *   "proposalId": "&lt;uuid&gt;"
     * }
     * </pre>
     */
    @Override
    public void sendMatchFound(String fcmToken, String intentId, String proposalId) {
        Message message = Message.builder()
                .setToken(fcmToken)
                .putData("type",       "MATCH_FOUND")
                .putData("intentId",   intentId)
                .putData("proposalId", proposalId)
                .build();

        try {
            String messageId = firebaseMessaging.send(message);
            log.debug("FCM MATCH_FOUND dispatched: messageId={} proposalId={} intentId={}",
                    messageId, proposalId, intentId);
        } catch (FirebaseMessagingException ex) {
            // Log the error but never propagate — a push failure must not roll back
            // the MatchProposal that was already persisted to the database.
            log.error("FCM MATCH_FOUND delivery failed: proposalId={} token=[{}…] code={} message={}",
                    proposalId,
                    fcmToken.substring(0, Math.min(12, fcmToken.length())),
                    ex.getMessagingErrorCode(),
                    ex.getMessage());
        }
    }
}
