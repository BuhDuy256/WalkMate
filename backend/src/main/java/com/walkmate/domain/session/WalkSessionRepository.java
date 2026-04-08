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
     * Returns PENDING sessions whose activation window has fully closed.
     * Window close = scheduledStart + {@link WalkSession#ACTIVATION_WINDOW_AFTER}.
     */
    List<WalkSession> findSessionsPastActivationWindow(Instant now);

    /**
     * Returns ACTIVE sessions whose scheduled end is before the given cutoff.
     * Caller passes {@code now − maxLifespan} as the cutoff.
     */
    List<WalkSession> findSessionsPastEndTime(Instant cutoff);

    /** Appends one row to the session_state_change_log audit table. */
    void logStateChange(String sessionId, SessionStatus from, SessionStatus to,
                        String changedBy, String reason);

    /**
     * Returns terminal sessions (COMPLETED, NO_SHOW, ABORTED, CANCELLED) for a user,
     * ordered by ended_at DESC (falling back to created_at). Capped at 50 rows.
     */
    List<WalkSession> findCompletedByUserId(String userId);
}
