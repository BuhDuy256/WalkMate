package com.walkmate.domain.notification;

import com.walkmate.domain.shared.exception.DomainException;
import lombok.Getter;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Getter
public class Notification {

    private String             notificationId;
    private String             userId;
    private NotificationType   type;
    private Map<String, Object> payload;
    private NotificationStatus status;
    private Instant            createdAt;
    private Instant            readAt;

    protected Notification() {}

    // ── Rehydration constructor (repository → domain) ─────────────────────────

    public Notification(String notificationId, String userId,
                        NotificationType type, Map<String, Object> payload,
                        NotificationStatus status,
                        Instant createdAt, Instant readAt) {
        this.notificationId = notificationId;
        this.userId         = userId;
        this.type           = type;
        this.payload        = payload;
        this.status         = status;
        this.createdAt      = createdAt;
        this.readAt         = readAt;
    }

    // ── Creation factory ──────────────────────────────────────────────────────

    public static Notification create(String userId, NotificationType type,
                                      Map<String, Object> payload) {
        Notification n   = new Notification();
        n.notificationId = UUID.randomUUID().toString();
        n.userId         = userId;
        n.type           = type;
        n.payload        = payload != null ? payload : Map.of();
        n.status         = NotificationStatus.PENDING;
        n.createdAt      = Instant.now();
        return n;
    }

    // ── Domain behaviour ──────────────────────────────────────────────────────

    /**
     * Marks this notification as READ. Idempotent — calling again is a no-op.
     *
     * @param callerId the authenticated user requesting the read; must own this notification.
     */
    public void markRead(String callerId) {
        if (!this.userId.equals(callerId)) {
            throw new DomainException(NotificationErrorCode.NOTIFICATION_NOT_OWNER);
        }
        if (this.status == NotificationStatus.READ) {
            return; // idempotent
        }
        this.status  = NotificationStatus.READ;
        this.readAt  = Instant.now();
    }
}
