package com.walkmate.core.event;

import java.util.Collections;
import java.util.Map;

/**
 * Immutable event payload posted to AppEventBus.
 *
 * The {@link #payload} map carries the raw FCM data key/value pairs (e.g.
 * {@code proposalId}, {@code sessionId}, {@code requestId}). Consumers read
 * payload values by key rather than through typed fields so that adding a new
 * key never requires a constructor change.
 *
 * Intent extra keys ({@link #EXTRA_FCM_TYPE} etc.) are shared between this
 * class and {@code WalkMateFcmService} so the background-tray PendingIntent and
 * {@code MainActivity.handleFcmIntent()} use the same contract.
 */
public class AppEvent {

    // ── Intent extra keys (background deep-link via system tray PendingIntent) ─

    /** String extra placed on the MainActivity launch Intent by WalkMateFcmService. */
    public static final String EXTRA_FCM_TYPE        = "fcm_type";
    /** Optional proposalId placed alongside {@link #EXTRA_FCM_TYPE}. */
    public static final String EXTRA_FCM_PROPOSAL_ID = "fcm_proposalId";
    /** Optional sessionId placed alongside {@link #EXTRA_FCM_TYPE}. */
    public static final String EXTRA_FCM_SESSION_ID  = "fcm_sessionId";

    // ── Payload keys (also used as FCM data keys on the backend) ─────────────

    public static final String KEY_PROPOSAL_ID = "proposalId";
    public static final String KEY_SESSION_ID  = "sessionId";
    public static final String KEY_INTENT_ID   = "intentId";

    // ── Event type enum ───────────────────────────────────────────────────────

    public enum Type {
        MATCH_FOUND,
        PROPOSAL_RECEIVED,
        INVITE_SENT,
        PROPOSAL_ACCEPTED,
        SESSION_CONFIRMED,
        SESSION_ACTIVE,
        FRIEND_REQUEST_RECEIVED,
        FRIEND_REQUEST_ACCEPTED,
        FRIEND_REQUEST_DECLINED
    }

    // ── Payload ───────────────────────────────────────────────────────────────

    public final Type               type;
    public final Map<String, String> payload;

    public AppEvent(Type type, Map<String, String> payload) {
        this.type    = type;
        this.payload = payload != null ? Collections.unmodifiableMap(payload)
                                       : Collections.emptyMap();
    }
}
