package com.walkmate.domain.session;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface WalkSessionRepository {

    WalkSession save(WalkSession session);

    Optional<WalkSession> findById(String sessionId);

    Optional<WalkSession> findByProposalId(String proposalId);

    List<WalkSession> findActiveForUser(String userId);

    /** True when the user already has a session with overlapping time that is PENDING or ACTIVE. */
    boolean hasOverlappingActiveSession(String userId, Instant start, Instant end);

    /**
      * Returns PENDING sessions that have stayed unresolved past a safety TTL.
      * Caller passes {@code now - pendingTtl} as the cutoff.
     */
     List<WalkSession> findStalePendingSessions(Instant cutoff);

    /**
     * Returns ACTIVE sessions whose scheduled end is before the given cutoff.
     * Caller passes {@code now − maxLifespan} as the cutoff.
     */
    List<WalkSession> findSessionsPastEndTime(Instant cutoff);

    /**
     * Returns PENDING sessions whose activation deadline has passed.
     * Caller passes {@code now − ACTIVATION_WINDOW_AFTER} as the cutoff so that
     * any session whose scheduled_start is before the cutoff has definitively
     * closed its arrival window and should be marked as a no-show.
     */
    List<WalkSession> findPendingSessionsPastNoShowDeadline(Instant cutoff);

    /** Appends one row to the session_state_change_log audit table. */
    void logStateChange(String sessionId, SessionStatus from, SessionStatus to,
                        String changedBy, String reason);

    /** Returns history sessions (ACTIVE, COMPLETED, NO_SHOW) for a user, newest first. */
    List<WalkSession> findHistoryByUserId(String userId);
}
