package com.walkmate.service;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.walkmate.R;
import com.walkmate.WalkMateApplication;
import com.walkmate.core.event.AppEvent;
import com.walkmate.core.event.AppEventBus;
import com.walkmate.domain.shared.DomainCallback;
import com.walkmate.domain.user.UserRepository;
import com.walkmate.ui.main.MainActivity;

import java.util.HashMap;
import java.util.Map;

/**
 * Handles Firebase Cloud Messaging events.
 *
 * <p><b>Token registration</b>: {@link #onNewToken} fires on first launch and whenever
 * FCM rotates the token. Sent to the backend via PATCH /api/v1/users/me/fcm-token.
 * Silently ignored when the user is not authenticated.</p>
 *
 * <p><b>Message routing</b>: All messages use data-only payloads (no {@code notification}
 * block), so {@link #onMessageReceived} is always invoked regardless of foreground/background
 * state, giving the client full control over presentation.</p>
 *
 * <p>Two routing paths:</p>
 * <ol>
 *   <li><b>Foreground</b>: posts an {@link AppEvent} on {@link AppEventBus}; the active
 *       {@code MainActivity} observer navigates immediately.</li>
 *   <li><b>Background / killed</b>: posts the same AppEvent (queued until Activity resumes)
 *       AND shows a system-tray notification whose {@link PendingIntent} carries
 *       {@link AppEvent#EXTRA_FCM_TYPE} and the relevant ID as Intent extras.
 *       {@code MainActivity.handleFcmIntent()} reads these extras on launch.</li>
 * </ol>
 */
public class WalkMateFcmService extends FirebaseMessagingService {

    private static final String CHANNEL_ID   = "walkmate_push";
    private static final String CHANNEL_NAME = "WalkMate Notifications";

    // ── Token registration ────────────────────────────────────────────────────

    @Override
    public void onNewToken(@NonNull String token) {
        WalkMateApplication app = (WalkMateApplication) getApplicationContext();
        UserRepository userRepo = app.getUserRepository();

        userRepo.updateFcmToken(token, new DomainCallback<Void>() {
            @Override public void onSuccess(Void result) { /* nothing to do */ }
            @Override public void onError(Exception error) {
                // Silently ignore: common when the user is not yet authenticated.
            }
        });
    }

    // ── Message handler ───────────────────────────────────────────────────────

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        Map<String, String> data = remoteMessage.getData();
        String type = data.get("type");
        if (type == null) return;

        // Build a copy of the full FCM data map as the AppEvent payload.
        Map<String, String> payload = new HashMap<>(data);

        AppEvent.Type eventType = resolveEventType(type);
        if (eventType == null) return; // unknown type — ignore

        // 1. Post to AppEventBus (foreground path — LiveData observer in MainActivity).
        AppEventBus.get().post(new AppEvent(eventType, payload));

        // 2. Show system-tray notification (background path — user taps to open app).
        showTrayNotification(eventType, payload);
    }

    // ── Event type resolution ─────────────────────────────────────────────────

    private AppEvent.Type resolveEventType(String type) {
        switch (type) {
            case "MATCH_FOUND":              return AppEvent.Type.MATCH_FOUND;
            case "PROPOSAL_RECEIVED":        return AppEvent.Type.PROPOSAL_RECEIVED;
            case "INVITE_SENT":              return AppEvent.Type.INVITE_SENT;
            case "PROPOSAL_ACCEPTED":        return AppEvent.Type.PROPOSAL_ACCEPTED;
            case "SESSION_CONFIRMED":        return AppEvent.Type.SESSION_CONFIRMED;
            case "SESSION_ACTIVE":           return AppEvent.Type.SESSION_ACTIVE;
            case "FRIEND_REQUEST_RECEIVED":  return AppEvent.Type.FRIEND_REQUEST_RECEIVED;
            case "FRIEND_REQUEST_ACCEPTED":  return AppEvent.Type.FRIEND_REQUEST_ACCEPTED;
            case "FRIEND_REQUEST_DECLINED":  return AppEvent.Type.FRIEND_REQUEST_DECLINED;
            default:                         return null;
        }
    }

    // ── System-tray notification ──────────────────────────────────────────────

    /**
     * Builds and posts a system-tray notification. When the user taps it,
     * {@code MainActivity} is launched (or brought to front) with FCM type/ID
     * extras so {@code handleFcmIntent()} can deep-link to the right screen.
     */
    private void showTrayNotification(AppEvent.Type eventType,
                                      Map<String, String> payload) {
        ensureNotificationChannel();

        // Build the deep-link Intent for when the user taps the notification.
        Intent launchIntent = new Intent(this, MainActivity.class);
        launchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        launchIntent.putExtra(AppEvent.EXTRA_FCM_TYPE, eventType.name());

        String proposalId = payload.get(AppEvent.KEY_PROPOSAL_ID);
        if (proposalId != null) {
            launchIntent.putExtra(AppEvent.EXTRA_FCM_PROPOSAL_ID, proposalId);
        }
        String sessionId = payload.get(AppEvent.KEY_SESSION_ID);
        if (sessionId != null) {
            launchIntent.putExtra(AppEvent.EXTRA_FCM_SESSION_ID, sessionId);
        }

        // Build a unique key so each distinct (type, session, proposal) triple gets its
        // own tray slot. Concatenating both IDs (defaulting to "") avoids a collision
        // where e.g. SESSION_CONFIRMED+"abc" == PROPOSAL_RECEIVED+"abc".
        int notificationId = (eventType.name()
                + payload.getOrDefault(AppEvent.KEY_SESSION_ID,  "")
                + payload.getOrDefault(AppEvent.KEY_PROPOSAL_ID, "")
        ).hashCode();

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                notificationId,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_bell)
                .setContentTitle(titleFor(eventType))
                .setContentText(bodyFor(eventType))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        NotificationManagerCompat.from(this).notify(notificationId, builder.build());
    }

    private void ensureNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("Real-time WalkMate activity notifications");
        NotificationManager manager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }

    private String titleFor(AppEvent.Type type) {
        switch (type) {
            case MATCH_FOUND:
            case PROPOSAL_RECEIVED:       return "New Walk Match";
            case INVITE_SENT:             return "Invite Sent";
            case PROPOSAL_ACCEPTED:       return "Proposal Accepted";
            case SESSION_CONFIRMED:       return "Walk Confirmed!";
            case SESSION_ACTIVE:          return "Walk Started";
            case FRIEND_REQUEST_RECEIVED: return "New Friend Request";
            case FRIEND_REQUEST_ACCEPTED: return "Friend Request Accepted";
            case FRIEND_REQUEST_DECLINED: return "Friend Request Declined";
            default:                      return "WalkMate";
        }
    }

    private String bodyFor(AppEvent.Type type) {
        switch (type) {
            case MATCH_FOUND:
            case PROPOSAL_RECEIVED:       return "You have a new walk match proposal. Tap to review it.";
            case INVITE_SENT:             return "Your private invite has been dispatched.";
            case PROPOSAL_ACCEPTED:       return "Your partner accepted the proposal!";
            case SESSION_CONFIRMED:       return "Both of you accepted — your walk session is confirmed.";
            case SESSION_ACTIVE:          return "Both partners have arrived. Your walk is underway!";
            case FRIEND_REQUEST_RECEIVED: return "Someone sent you a friend request. Tap to review it.";
            case FRIEND_REQUEST_ACCEPTED: return "Your friend request was accepted!";
            case FRIEND_REQUEST_DECLINED: return "Your friend request was declined.";
            default:                      return "";
        }
    }
}
