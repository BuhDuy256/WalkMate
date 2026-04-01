package com.walkmate.application.notification;

import com.walkmate.domain.notification.Notification;
import com.walkmate.domain.notification.NotificationErrorCode;
import com.walkmate.domain.notification.NotificationRepository;
import com.walkmate.domain.shared.exception.DomainException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationCommandService {

    private final NotificationRepository notificationRepository;

    // ── Queries ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Notification> listForUser(String userId) {
        return notificationRepository.findByUserId(userId);
    }

    // ── Commands ──────────────────────────────────────────────────────────────

    /**
     * Marks a single notification as READ. Idempotent.
     *
     * @param notificationId the notification to mark
     * @param callerId       the authenticated user (must own the notification)
     */
    @Transactional
    public void markRead(String notificationId, String callerId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new DomainException(NotificationErrorCode.NOTIFICATION_NOT_FOUND));

        notification.markRead(callerId);
        notificationRepository.save(notification);
    }
}
