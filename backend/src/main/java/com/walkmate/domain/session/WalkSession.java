package com.walkmate.domain.session;

import com.walkmate.domain.shared.exception.DomainException;
import lombok.Getter;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Getter
public class WalkSession {

    /** Arrival window opens this many minutes before the scheduled start. */
    public static final Duration ACTIVATION_WINDOW_BEFORE = Duration.ofMinutes(180);
    /** Arrival window closes this many minutes after the scheduled start. */
    public static final Duration ACTIVATION_WINDOW_AFTER  = Duration.ofMinutes(180);

    private String        sessionId;
    private String        proposalId;
    private String        userIdA;
    private String        userIdB;
    private double        meetingPointLat;
    private double        meetingPointLng;
    private Instant       scheduledStart;
    private Instant       scheduledEnd;

    // ── Global session status (derived from per-user statuses) ────────────────
    private SessionStatus status;
    private Instant       createdAt;
    /** Set when the first participant activates; used by scheduler/audit. */
    private Instant       startedAt;
    /** Set when the last participant reaches a terminal state. */
    private Instant       endedAt;

    // ── Per-participant arrival timestamps ────────────────────────────────────
    private Instant userAActivatedAt;
    private Instant userBActivatedAt;

    // ── Per-participant independent lifecycle ─────────────────────────────────
    private SessionStatus userAStatus;
    private SessionStatus userBStatus;
    private Instant       userAEndedAt;
    private Instant       userBEndedAt;

    // ── Per-participant walk metrics ──────────────────────────────────────────
    private double userADistanceKm;
    private long   userADurationSeconds;
    private double userBDistanceKm;
    private long   userBDurationSeconds;

    // ── Shared cancellation metadata ──────────────────────────────────────────
    private String      cancellationReason;
    private String      cancelledBy;    // UUID string; null when system-initiated
    private long        version;

    protected WalkSession() {}

    // ── Rehydration constructor (repository → domain) ─────────────────────────

    public WalkSession(String sessionId, String proposalId,
                       String userIdA, String userIdB,
                       double meetingPointLat, double meetingPointLng,
                       Instant scheduledStart, Instant scheduledEnd,
                       SessionStatus status,
                       Instant createdAt, Instant startedAt, Instant endedAt,
                       Instant userAActivatedAt, Instant userBActivatedAt,
                       String cancellationReason, String cancelledBy,
                       long version,
                       SessionStatus userAStatus, SessionStatus userBStatus,
                       Instant userAEndedAt, Instant userBEndedAt,
                       double userADistanceKm, long userADurationSeconds,
                       double userBDistanceKm, long userBDurationSeconds) {
        this.sessionId             = sessionId;
        this.proposalId            = proposalId;
        this.userIdA               = userIdA;
        this.userIdB               = userIdB;
        this.meetingPointLat       = meetingPointLat;
        this.meetingPointLng       = meetingPointLng;
        this.scheduledStart        = scheduledStart;
        this.scheduledEnd          = scheduledEnd;
        this.status                = status;
        this.createdAt             = createdAt;
        this.startedAt             = startedAt;
        this.endedAt               = endedAt;
        this.userAActivatedAt      = userAActivatedAt;
        this.userBActivatedAt      = userBActivatedAt;
        this.cancellationReason    = cancellationReason;
        this.cancelledBy           = cancelledBy;
        this.version               = version;
        this.userAStatus           = userAStatus;
        this.userBStatus           = userBStatus;
        this.userAEndedAt          = userAEndedAt;
        this.userBEndedAt          = userBEndedAt;
        this.userADistanceKm       = userADistanceKm;
        this.userADurationSeconds  = userADurationSeconds;
        this.userBDistanceKm       = userBDistanceKm;
        this.userBDurationSeconds  = userBDurationSeconds;
    }

    // ── Creation factory ──────────────────────────────────────────────────────

    private WalkSession(String proposalId, String userIdA, String userIdB,
                        double meetingPointLat, double meetingPointLng,
                        Instant scheduledStart, Instant scheduledEnd) {
        this.sessionId       = UUID.randomUUID().toString();
        this.proposalId      = proposalId;
        this.userIdA         = userIdA;
        this.userIdB         = userIdB;
        this.meetingPointLat = meetingPointLat;
        this.meetingPointLng = meetingPointLng;
        this.scheduledStart  = scheduledStart;
        this.scheduledEnd    = scheduledEnd;
        this.status          = SessionStatus.PENDING;
        this.userAStatus     = SessionStatus.PENDING;
        this.userBStatus     = SessionStatus.PENDING;
        this.createdAt       = Instant.now();
        this.version         = 0;
    }

    public static WalkSession create(String proposalId, String userIdA, String userIdB,
                                     double meetingPointLat, double meetingPointLng,
                                     Instant scheduledStart, Instant scheduledEnd) {
        return new WalkSession(proposalId, userIdA, userIdB,
                meetingPointLat, meetingPointLng, scheduledStart, scheduledEnd);
    }

    // ── Domain behaviour ──────────────────────────────────────────────────────

    /**
     * Records that a participant has arrived at the meeting point (S-2 revised).
     * Each user independently transitions to ACTIVE. The global status becomes
     * ACTIVE as soon as the first participant activates.
     */
    public void recordActivation(String userId, Instant now) {
        // Per-user guard only — global may already be ACTIVE if partner arrived first (S-2).
        if (userId.equals(userIdA)) {
            if (this.userAStatus != SessionStatus.PENDING) {
                throw new DomainException(SessionErrorCode.SESSION_NOT_PENDING);
            }
            this.userAStatus      = SessionStatus.ACTIVE;
            this.userAActivatedAt = now;
        } else if (userId.equals(userIdB)) {
            if (this.userBStatus != SessionStatus.PENDING) {
                throw new DomainException(SessionErrorCode.SESSION_NOT_PENDING);
            }
            this.userBStatus      = SessionStatus.ACTIVE;
            this.userBActivatedAt = now;
        } else {
            throw new DomainException(SessionErrorCode.SESSION_NOT_PARTICIPANT);
        }

        // Global startedAt marks when the first participant begins walking.
        if (this.startedAt == null) {
            this.startedAt = now;
        }

        deriveGlobalStatus(now);
        this.version++;
    }

    /**
     * Marks a specific participant's walk as completed (S-5 revised).
     * The global session transitions to COMPLETED only when both participants
     * have reached a terminal state and at least one is COMPLETED.
     *
     * @param userId the completing participant's ID
     * @param now    current timestamp
     */
    public void complete(String userId, Instant now) {
        SessionStatus current = participantStatus(userId);
        if (current != SessionStatus.ACTIVE) {
            throw new DomainException(SessionErrorCode.SESSION_NOT_ACTIVE);
        }

        if (userId.equals(userIdA)) {
            this.userAStatus          = SessionStatus.COMPLETED;
            this.userAEndedAt         = now;
            this.userADurationSeconds = userAActivatedAt != null
                    ? Duration.between(userAActivatedAt, now).getSeconds() : 0L;
        } else {
            this.userBStatus          = SessionStatus.COMPLETED;
            this.userBEndedAt         = now;
            this.userBDurationSeconds = userBActivatedAt != null
                    ? Duration.between(userBActivatedAt, now).getSeconds() : 0L;
        }

        deriveGlobalStatus(now);
        this.version++;
    }

    /**
     * Scheduler-driven bulk completion. Completes every participant that is
     * still ACTIVE (S-6 safety ceiling). Used only by the scheduled sweep.
     */
    public void complete(Instant now) {
        if (this.status != SessionStatus.ACTIVE) {
            throw new DomainException(SessionErrorCode.SESSION_NOT_ACTIVE);
        }
        if (userAStatus == SessionStatus.ACTIVE) {
            this.userAStatus          = SessionStatus.COMPLETED;
            this.userAEndedAt         = now;
            this.userADurationSeconds = userAActivatedAt != null
                    ? Duration.between(userAActivatedAt, now).getSeconds() : 0L;
        }
        if (userBStatus == SessionStatus.ACTIVE) {
            this.userBStatus          = SessionStatus.COMPLETED;
            this.userBEndedAt         = now;
            this.userBDurationSeconds = userBActivatedAt != null
                    ? Duration.between(userBActivatedAt, now).getSeconds() : 0L;
        }
        this.status  = SessionStatus.COMPLETED;
        this.endedAt = now;
        this.version++;
    }

    /**
     * Cancels a PENDING session before either participant has started walking.
     * Both users must still be in PENDING state (i.e. global is PENDING).
     */
    public void cancel(String reason, String cancelledBy) {
        if (this.status != SessionStatus.PENDING) {
            throw new DomainException(SessionErrorCode.SESSION_CANCEL_NOT_PENDING);
        }
        this.userAStatus          = SessionStatus.CANCELLED;
        this.userBStatus          = SessionStatus.CANCELLED;
        this.status               = SessionStatus.CANCELLED;
        this.cancellationReason   = reason;
        this.cancelledBy          = cancelledBy;
        this.version++;
    }

    /**
     * Marks a specific participant as NO_SHOW. The global status derives to
     * COMPLETED once both participants are in a terminal state (per invariant table).
     * Valid only while the global session is still PENDING (neither user activated).
     */
    public void markNoShow(String userId, Instant now) {
        if (this.status != SessionStatus.PENDING) {
            throw new DomainException(SessionErrorCode.SESSION_NOT_PENDING);
        }
        if (userId.equals(userIdA)) {
            this.userAStatus  = SessionStatus.NO_SHOW;
            this.userAEndedAt = now;
        } else if (userId.equals(userIdB)) {
            this.userBStatus  = SessionStatus.NO_SHOW;
            this.userBEndedAt = now;
        } else {
            throw new DomainException(SessionErrorCode.SESSION_NOT_PARTICIPANT);
        }
        deriveGlobalStatus(now);
        this.version++;
    }

    /**
     * Marks both participants as NO_SHOW at once (scheduler use only — neither showed up).
     */
    public void markBothNoShow(Instant now) {
        if (this.status != SessionStatus.PENDING) {
            throw new DomainException(SessionErrorCode.SESSION_NOT_PENDING);
        }
        this.userAStatus  = SessionStatus.NO_SHOW;
        this.userBStatus  = SessionStatus.NO_SHOW;
        this.userAEndedAt = now;
        this.userBEndedAt = now;
        deriveGlobalStatus(now);
        this.version++;
    }

    /**
     * Persists the GPS-derived distance for one participant.
     * Called by GamificationCommandService after aggregating polyline chunks.
     */
    public void recordFinalDistance(String userId, double distanceKm) {
        double km = Math.max(0.0, distanceKm);
        if (userId.equals(userIdA)) {
            this.userADistanceKm = km;
        } else if (userId.equals(userIdB)) {
            this.userBDistanceKm = km;
        } else {
            throw new DomainException(SessionErrorCode.SESSION_NOT_PARTICIPANT);
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Derives the global session status from the two per-participant statuses.
     *
     * Exhaustive, mutually exclusive rules:
     *   Rule 1 — Both PENDING                          → PENDING
     *   Rule 2 — Both in {COMPLETED, NO_SHOW}          → COMPLETED
     *   Rule 3 — Any other combination                 → ACTIVE
     *
     * This ensures a session can never stay PENDING once any user has moved
     * past PENDING (e.g. PENDING + COMPLETED correctly resolves to ACTIVE,
     * not PENDING). CANCELLED is set directly by {@link #cancel}.
     */
    private void deriveGlobalStatus(Instant now) {
        // Rule 1
        if (userAStatus == SessionStatus.PENDING && userBStatus == SessionStatus.PENDING) {
            this.status = SessionStatus.PENDING;
            return;
        }
        // Rule 2
        if (isTerminal(userAStatus) && isTerminal(userBStatus)) {
            this.status  = SessionStatus.COMPLETED;
            this.endedAt = now;
            return;
        }
        // Rule 3 — any mix involving ACTIVE, or one PENDING + one terminal
        this.status = SessionStatus.ACTIVE;
    }

    private SessionStatus participantStatus(String userId) {
        if (userId.equals(userIdA)) return userAStatus;
        if (userId.equals(userIdB)) return userBStatus;
        throw new DomainException(SessionErrorCode.SESSION_NOT_PARTICIPANT);
    }

    private static boolean isTerminal(SessionStatus s) {
        return s == SessionStatus.COMPLETED || s == SessionStatus.NO_SHOW;
    }
}
