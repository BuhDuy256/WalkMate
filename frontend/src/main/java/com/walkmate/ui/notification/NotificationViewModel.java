package com.walkmate.ui.notification;

import android.os.Handler;
import android.os.Looper;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.walkmate.domain.notification.Notification;
import com.walkmate.domain.notification.NotificationRepository;
import com.walkmate.domain.shared.DomainCallback;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

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
            loadNotifications(false);
            mainHandler.postDelayed(this, POLL_INTERVAL_MS);
        }
    };

    public NotificationViewModel(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public LiveData<NotificationUiState> getUiState() { return uiState; }

    public void startPolling() {
        polling = true;
        loadNotifications(true);
        mainHandler.postDelayed(pollRunnable, POLL_INTERVAL_MS);
    }

    public void stopPolling() {
        polling = false;
        mainHandler.removeCallbacks(pollRunnable);
    }

    public void markRead(String notificationId) {
        notificationRepository.markRead(notificationId, new DomainCallback<Void>() {
            @Override public void onSuccess(Void result) { loadNotifications(false); }
            @Override public void onError(Exception error) { /* non-critical */ }
        });
    }

    public void markAllRead() {
        NotificationUiState current = uiState.getValue();
        if (current == null || current.kind != NotificationUiState.Kind.READY) return;

        List<Notification> unread = new ArrayList<>();
        for (Notification n : current.notifications) {
            if (!n.isRead()) unread.add(n);
        }
        if (unread.isEmpty()) return;

        AtomicInteger remaining = new AtomicInteger(unread.size());
        for (Notification n : unread) {
            notificationRepository.markRead(n.getNotificationId(), new DomainCallback<Void>() {
                @Override public void onSuccess(Void r) {
                    if (remaining.decrementAndGet() == 0) loadNotifications(false);
                }
                @Override public void onError(Exception e) {
                    if (remaining.decrementAndGet() == 0) loadNotifications(false);
                }
            });
        }
    }

    @Override
    protected void onCleared() { stopPolling(); }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void loadNotifications(boolean showLoader) {
        if (showLoader) uiState.postValue(NotificationUiState.loading());

        notificationRepository.getNotifications(new DomainCallback<List<Notification>>() {
            @Override public void onSuccess(List<Notification> result) {
                uiState.postValue(NotificationUiState.ready(result));
            }
            @Override public void onError(Exception error) {
                uiState.postValue(NotificationUiState.error(error.getMessage()));
            }
        });
    }
}
