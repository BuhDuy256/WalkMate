package com.walkmate.core.event;

import android.os.Handler;
import android.os.Looper;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

/**
 * Process-singleton event bus for authentication lifecycle events.
 *
 * Bridges OkHttp background threads (TokenRefreshAuthenticator) to foreground
 * LiveData observers (AuthActivity, MainActivity).
 *
 * Sticky-event guard:
 *   Observers MUST call consumeEvent() after handling FORCE_LOGOUT.
 *   Otherwise a config-change (rotation) will re-deliver the event.
 */
public class AuthEventBus {

    private static AuthEventBus instance;

    private final MutableLiveData<AuthEvent> events = new MutableLiveData<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private AuthEventBus() {}

    public static AuthEventBus getInstance() {
        if (instance == null) {
            instance = new AuthEventBus();
        }
        return instance;
    }

    public LiveData<AuthEvent> observe() {
        return events;
    }

    /**
     * Posts FORCE_LOGOUT from any thread. Marshals onto the main thread so
     * LiveData observers receive it safely.
     */
    public void postForceLogout() {
        mainHandler.post(() -> events.setValue(AuthEvent.FORCE_LOGOUT));
    }

    /**
     * Nulls out the current event so re-subscribed observers do not re-process
     * a stale FORCE_LOGOUT after rotation.
     */
    public void consumeEvent() {
        events.setValue(null);
    }
}
