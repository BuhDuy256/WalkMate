package com.walkmate.core.event;

import android.os.Handler;
import android.os.Looper;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

/**
 * Process-singleton event bus bridging FCM background threads to foreground
 * LiveData observers (typically MainActivity).
 *
 * Why LiveData instead of a plain callback:
 *   - Lifecycle-safe: observers only run when the host (Activity/Fragment) is STARTED.
 *   - Automatic cleanup: no manual unsubscribe needed.
 *
 * Sticky-event guard:
 *   Callers MUST invoke consumeEvent() after handling an event. If they don't,
 *   a config-change (rotation) will re-deliver the last event to the new observer.
 *   This mirrors the consumeError() pattern used in UiState across the app.
 */
public class AppEventBus {

    private static AppEventBus instance;

    private final MutableLiveData<AppEvent> events = new MutableLiveData<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private AppEventBus() {}

    public static AppEventBus get() {
        if (instance == null) {
            instance = new AppEventBus();
        }
        return instance;
    }

    public LiveData<AppEvent> observe() {
        return events;
    }

    /**
     * Posts an event from any thread (FCM callbacks run on a background thread).
     * Marshals onto the main thread so LiveData observers receive it safely.
     */
    public void post(AppEvent event) {
        mainHandler.post(() -> events.setValue(event));
    }

    /**
     * Nulls out the current event so re-subscribed observers (e.g., after rotation)
     * do not re-process a stale event.
     */
    public void consumeEvent() {
        events.setValue(null);
    }
}
