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
    private SessionStatus status;
    private Instant       createdAt;
    private Instant       startedAt;
    private Instant       endedAt;

    // ── Phase 5 fields ────────────────────────────────────────────────────────
    private Instant     userAActivatedAt;
    private Instant     userBActivatedAt;
    private String      cancellationReason;
    private String      cancelledBy;    // UUID string; null when system-initiated
    private AbortReason abortReason;
    private long        version;

    // ── Walk outcome stats (persisted at completion/abort) ────────────────────
    /** Total GPS distance in kilometres. Set by GamificationCommandService after polyline calculation. */
    private double totalDistanceKm;
    /** Walk duration in seconds from activation to end. Set by complete() / abort(). */
    private long   totalDurationSeconds;

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
                       double totalDistanceKm, long totalDurationSeconds) {
        this.sessionId            = sessionId;
        this.proposalId           = proposalId;
        this.userIdA              = userIdA;
        this.userIdB              = userIdB;
        this.meetingPointLat      = meetingPointLat;
        this.meetingPointLng      = meetingPointLng;
        this.scheduledStart       = scheduledStart;
        this.scheduledEnd         = scheduledEnd;
        this.status               = status;
        this.createdAt            = createdAt;
        this.startedAt            = startedAt;
        this.endedAt              = endedAt;
        this.userAActivatedAt     = userAActivatedAt;
        this.userBActivatedAt     = userBActivatedAt;
        this.cancellationReason   = cancellationReason;
        this.cancelledBy          = cancelledBy;
        this.abortReason          = abortReason;
        this.version              = version;
        this.totalDistanceKm      = totalDistanceKm;
        this.totalDurationSeconds = totalDurationSeconds;
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
     * Records that a participant has arrived at the meeting point (S-3/S-4).
     * Transitions the session to ACTIVE once both users have activated.
     *
     * @param userId      the arriving user's ID
     * @param now         current timestamp
    * @param windowOpen  earliest moment a user may activate (scheduledStart - 180 min)
    * @param windowClose latest moment a user may activate  (scheduledStart + 180 min)
     */
    public void recordActivation(String userId, Instant now, Instant windowOpen, Instant windowClose) {
        if (this.status != SessionStatus.PENDING) {
            throw new DomainException(SessionErrorCode.SESSION_NOT_PENDING);
        }
        if (now.isBefore(windowOpen) || now.isAfter(windowClose)) {
            throw new DomainException(SessionErrorCode.SESSION_ACTIVATION_WINDOW_CLOSED);
        }

        if (userId.equals(userIdA)) {
            this.userAActivatedAt = now;
        } else if (userId.equals(userIdB)) {
            this.userBActivatedAt = now;
        } else {
            throw new DomainException(SessionErrorCode.SESSION_NOT_PARTICIPANT);
        }

        // Both arrived — transition to ACTIVE
        if (this.userAActivatedAt != null && this.userBActivatedAt != null) {
            this.status    = SessionStatus.ACTIVE;
            this.startedAt = now;
        }
        
        this.version++;
    }

    /**
     * Cancels a PENDING session before the walk starts (S-6).
     *
     * @param reason      human-readable reason
     * @param cancelledBy UUID string of the user who cancelled; null for system-initiated
     */
    public void cancel(String reason, String cancelledBy) {
        if (this.status != SessionStatus.PENDING) {
            throw new DomainException(SessionErrorCode.SESSION_CANCEL_NOT_PENDING);
        }
        this.status             = SessionStatus.CANCELLED;
        this.cancellationReason = reason;
        this.cancelledBy        = cancelledBy;
        this.version++;
    }

    /**
     * Aborts an ACTIVE session mid-walk (C-2 resolution).
     * Records partial walk duration; distance stays 0 (no gamification on abort).
     */
    public void abort(AbortReason reason, Instant now) {
        if (this.status != SessionStatus.ACTIVE) {
            throw new DomainException(SessionErrorCode.SESSION_NOT_ACTIVE);
        }
        this.status               = SessionStatus.ABORTED;
        this.abortReason          = reason;
        this.endedAt              = now;
        this.totalDurationSeconds = startedAt != null
                ? Duration.between(startedAt, now).getSeconds() : 0L;
        this.version++;
    }

    /**
     * Completes an ACTIVE session normally (S-7/S-9).
     * Records walk duration; distance is populated later by
     * {@link #recordFinalDistance(double)} once polylines are aggregated.
     */
    public void complete(Instant now) {
        if (this.status != SessionStatus.ACTIVE) {
            throw new DomainException(SessionErrorCode.SESSION_NOT_ACTIVE);
        }
        this.status               = SessionStatus.COMPLETED;
        this.endedAt              = now;
        this.totalDurationSeconds = startedAt != null
                ? Duration.between(startedAt, now).getSeconds() : 0L;
        this.version++;
    }

    /**
     * Persists the GPS-derived total distance for this session.
     * Called by {@code GamificationCommandService} after aggregating polyline chunks.
     *
     * @param distanceKm total distance walked in kilometres (≥ 0)
     */
    public void recordFinalDistance(double distanceKm) {
        this.totalDistanceKm = Math.max(0.0, distanceKm);
    }

    /**
     * Marks a PENDING session as NO_SHOW when exactly one user arrived
     * and the activation window has closed (S-5).
     */
    public void markNoShow() {
        if (this.status != SessionStatus.PENDING) {
            throw new DomainException(SessionErrorCode.SESSION_NOT_PENDING);
        }
        this.status = SessionStatus.NO_SHOW;
        this.version++;
    }
}
