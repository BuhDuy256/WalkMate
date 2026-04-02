package com.walkmate.domain.notification;

import com.walkmate.domain.shared.DomainCallback;

import java.util.List;

public interface NotificationRepository {

    /** Fetches all notifications for the authenticated user, newest first. */
    void getNotifications(DomainCallback<List<Notification>> callback);

    /** Marks a notification as READ on the backend. */
    void markRead(String notificationId, DomainCallback<Void> callback);
}
