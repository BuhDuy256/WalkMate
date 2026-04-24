package com.walkmate.application.gamification;

import java.time.Instant;

/**
 * Spring ApplicationEvent fired after a user-initiated WalkSession cancellation.
 *
 * <p>Published by {@link com.walkmate.application.session.SessionCommandService}
 * inside the committing transaction. The {@link GamificationCommandService} listener
 * determines the correct penalty ({@code CANCELLED_LATE} vs {@code CANCELLED_EARLY})
 * by comparing {@code cancelledAt} against {@code scheduledStart}.
 *
 * <p>Only fired for user-initiated cancellations ({@code cancelledByUserId != null}).
 * System auto-cancellations (TTL sweep) do not publish this event.
 */
public class SessionCancelledEvent {

    private final String  sessionId;
    private final String  cancelledByUserId;
    private final Instant scheduledStart;
    private final Instant cancelledAt;

    public SessionCancelledEvent(String sessionId, String cancelledByUserId,
                                 Instant scheduledStart, Instant cancelledAt) {
        this.sessionId         = sessionId;
        this.cancelledByUserId = cancelledByUserId;
        this.scheduledStart    = scheduledStart;
        this.cancelledAt       = cancelledAt;
    }

    public String  getSessionId()          { return sessionId; }
    public String  getCancelledByUserId()  { return cancelledByUserId; }
    public Instant getScheduledStart()     { return scheduledStart; }
    public Instant getCancelledAt()        { return cancelledAt; }
}
