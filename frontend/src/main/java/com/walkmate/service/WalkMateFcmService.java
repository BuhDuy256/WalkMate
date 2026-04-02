package com.walkmate.service;

import androidx.annotation.NonNull;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.walkmate.WalkMateApplication;
import com.walkmate.core.event.AppEvent;
import com.walkmate.core.event.AppEventBus;
import com.walkmate.domain.shared.DomainCallback;
import com.walkmate.domain.user.UserRepository;

import java.util.Map;

/**
 * Handles Firebase Cloud Messaging events.
 *
 * Token registration:
 *   onNewToken() fires on first app launch and whenever FCM rotates the token.
 *   The token is sent to the backend (PATCH /api/v1/users/me/fcm-token) so the
 *   server knows which device to push to for this user.
 *
 *   If the user is not logged in yet (no access token), the call is silently
 *   skipped. The token will be registered the next time onNewToken() fires
 *   (FCM will retry) or the app can explicitly register it after login.
 *
 * Message routing:
 *   All messages use data-only payloads (no notification block). This guarantees
 *   that onMessageReceived() is called even when the app is in the foreground,
 *   giving us full control over how the event is surfaced.
 *
 *   MATCH_FOUND events are forwarded to AppEventBus so the foreground MainActivity
 *   can navigate to the Matches tab without a user tap on a system notification.
 */
public class WalkMateFcmService extends FirebaseMessagingService {

    @Override
    public void onNewToken(@NonNull String token) {
        // Fetch the repository from the Application-level Service Locator.
        // This is the mandated DI pattern for this project — no factory creation here.
        WalkMateApplication app = (WalkMateApplication) getApplicationContext();
        UserRepository userRepo = app.getUserRepository();

        userRepo.updateFcmToken(token, new DomainCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                // Token registered successfully — nothing to do in the service.
            }

            @Override
            public void onError(Exception error) {
                // Silently ignore: common when the user is not yet authenticated.
                // FCM will call onNewToken() again after the next app launch.
            }
        });
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        Map<String, String> data = remoteMessage.getData();
        String type = data.get("type");

        if ("MATCH_FOUND".equals(type)) {
            String intentId   = data.get("intentId");
            String proposalId = data.get("proposalId");
            AppEventBus.get().post(
                    new AppEvent(AppEvent.Type.MATCH_FOUND, intentId, proposalId));
        }
    }
}
