package com.walkmate.ui.notification;

import android.os.Handler;
import android.os.Looper;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.walkmate.domain.notification.Notification;
import com.walkmate.domain.notification.NotificationRepository;
import com.walkmate.domain.shared.DomainCallback;

import java.util.List;

/**
 * ViewModel for the Notification Center screen.
 *
 * <p>Polls {@code GET /api/v1/notifications} every {@link #POLL_INTERVAL_MS} ms
 * while the screen is in the foreground. Polling starts when the Fragment calls
 * {@link #startPolling()} (in {@code onResume}) and stops when it calls
 * {@link #stopPolling()} (in {@code onPause}).</p>
 */
public class NotificationViewModel extends ViewModel {

    private static final long POLL_INTERVAL_MS = 30_000L;

    private final MutableLiveData<NotificationUiState> uiState = new MutableLiveData<>();
    private final NotificationRepository notificationRepository;

    private final Handler  mainHandler = new Handler(Looper.getMainLooper());
    private boolean polling = false;

    private final Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            if (!polling) return;
            loadNotifications(false /* silent refresh — don't flash the loader */);
            mainHandler.postDelayed(this, POLL_INTERVAL_MS);
        }
    };

    public NotificationViewModel(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public LiveData<NotificationUiState> getUiState() {
        return uiState;
    }

    /** Called from Fragment.onResume — triggers an immediate load and starts polling. */
    public void startPolling() {
        polling = true;
        loadNotifications(true /* show loader on first fetch */);
        mainHandler.postDelayed(pollRunnable, POLL_INTERVAL_MS);
    }

    /** Called from Fragment.onPause — cancels pending poll callbacks. */
    public void stopPolling() {
        polling = false;
        mainHandler.removeCallbacks(pollRunnable);
    }

    /**
     * Marks a notification as read and refreshes the list silently.
     */
    public void markRead(String notificationId) {
        notificationRepository.markRead(notificationId, new DomainCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                loadNotifications(false);
            }

            @Override
            public void onError(Exception error) {
                // Non-critical — badge count may be stale until next poll
            }
        });
    }

    @Override
    protected void onCleared() {
        stopPolling();
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void loadNotifications(boolean showLoader) {
        if (showLoader) {
            uiState.postValue(NotificationUiState.loading());
        }

        notificationRepository.getNotifications(new DomainCallback<List<Notification>>() {
            @Override
            public void onSuccess(List<Notification> result) {
                uiState.postValue(NotificationUiState.ready(result));
            }

            @Override
            public void onError(Exception error) {
                uiState.postValue(NotificationUiState.error(error.getMessage()));
            }
        });
    }
}
