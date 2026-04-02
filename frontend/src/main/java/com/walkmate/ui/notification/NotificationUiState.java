package com.walkmate.ui.notification;

import com.walkmate.domain.notification.Notification;

import java.util.Collections;
import java.util.List;

public class NotificationUiState {

    public enum Kind { LOADING, READY, ERROR }

    public final Kind                kind;
    public final List<Notification>  notifications;
    public final int                 unreadCount;
    public final String              errorMessage;

    private NotificationUiState(Kind kind, List<Notification> notifications,
                                  int unreadCount, String errorMessage) {
        this.kind          = kind;
        this.notifications = notifications;
        this.unreadCount   = unreadCount;
        this.errorMessage  = errorMessage;
    }

    public static NotificationUiState loading() {
        return new NotificationUiState(Kind.LOADING, Collections.emptyList(), 0, null);
    }

    public static NotificationUiState ready(List<Notification> notifications) {
        int unread = 0;
        for (Notification n : notifications) {
            if (!n.isRead()) unread++;
        }
        return new NotificationUiState(Kind.READY, notifications, unread, null);
    }

    public static NotificationUiState error(String message) {
        return new NotificationUiState(Kind.ERROR, Collections.emptyList(), 0, message);
    }
}
