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

    // ── Shared cancellation / abort metadata ──────────────────────────────────
    private String      cancellationReason;
    private String      cancelledBy;    // UUID string; null when system-initiated
    private AbortReason abortReason;
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
                       AbortReason abortReason, long version,
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
        this.abortReason           = abortReason;
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
        if (this.status != SessionStatus.PENDING) {
            throw new DomainException(SessionErrorCode.SESSION_NOT_PENDING);
        }

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
     * Aborts an ACTIVE session mid-walk (emergency / safety case).
     * Terminates both participants immediately.
     */
    public void abort(AbortReason reason, Instant now) {
        if (this.status != SessionStatus.ACTIVE) {
            throw new DomainException(SessionErrorCode.SESSION_NOT_ACTIVE);
        }
        if (userAStatus == SessionStatus.ACTIVE) {
            this.userAStatus          = SessionStatus.ABORTED;
            this.userAEndedAt         = now;
            this.userADurationSeconds = userAActivatedAt != null
                    ? Duration.between(userAActivatedAt, now).getSeconds() : 0L;
        }
        if (userBStatus == SessionStatus.ACTIVE) {
            this.userBStatus          = SessionStatus.ABORTED;
            this.userBEndedAt         = now;
            this.userBDurationSeconds = userBActivatedAt != null
                    ? Duration.between(userBActivatedAt, now).getSeconds() : 0L;
        }
        this.status      = SessionStatus.ABORTED;
        this.abortReason = reason;
        this.endedAt     = now;
        this.version++;
    }

    /**
     * Marks a PENDING session as NO_SHOW (historical state, no longer auto-triggered).
     */
    public void markNoShow() {
        if (this.status != SessionStatus.PENDING) {
            throw new DomainException(SessionErrorCode.SESSION_NOT_PENDING);
        }
        this.userAStatus = SessionStatus.NO_SHOW;
        this.userBStatus = SessionStatus.NO_SHOW;
        this.status      = SessionStatus.NO_SHOW;
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
     * Rules (per S-2 / S-5 revised):
     * - ACTIVE  : at least one participant is ACTIVE (regardless of the other).
     * - COMPLETED: both participants are terminal AND at least one is COMPLETED.
     * - ABORTED  : both terminal, none COMPLETED, at least one ABORTED.
     * - CANCELLED: both terminal, none COMPLETED or ABORTED.
     * - PENDING  : neither participant has activated yet.
     */
    private void deriveGlobalStatus(Instant now) {
        boolean aTerminal = isTerminal(userAStatus);
        boolean bTerminal = isTerminal(userBStatus);

        if (aTerminal && bTerminal) {
            if (userAStatus == SessionStatus.COMPLETED || userBStatus == SessionStatus.COMPLETED) {
                this.status  = SessionStatus.COMPLETED;
            } else if (userAStatus == SessionStatus.ABORTED || userBStatus == SessionStatus.ABORTED) {
                this.status  = SessionStatus.ABORTED;
            } else {
                this.status  = SessionStatus.CANCELLED;
            }
            this.endedAt = now;
        } else if (userAStatus == SessionStatus.ACTIVE || userBStatus == SessionStatus.ACTIVE) {
            this.status = SessionStatus.ACTIVE;
        } else {
            this.status = SessionStatus.PENDING;
        }
    }

    private SessionStatus participantStatus(String userId) {
        if (userId.equals(userIdA)) return userAStatus;
        if (userId.equals(userIdB)) return userBStatus;
        throw new DomainException(SessionErrorCode.SESSION_NOT_PARTICIPANT);
    }

    private static boolean isTerminal(SessionStatus s) {
        return s == SessionStatus.COMPLETED
            || s == SessionStatus.CANCELLED
            || s == SessionStatus.ABORTED
            || s == SessionStatus.NO_SHOW;
    }
}
