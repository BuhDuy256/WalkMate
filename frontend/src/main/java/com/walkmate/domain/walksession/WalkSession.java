package com.walkmate.domain.walksession;

import java.time.Instant;

public class WalkSession {

    public enum Status {
        PENDING, ACTIVE, CANCELLED, COMPLETED, NO_SHOW
    }

    public static final long ACTIVATION_WINDOW_BEFORE_MINUTES = 180L;
    public static final long ACTIVATION_WINDOW_AFTER_MINUTES  = 180L;
    public static final long MINIMUM_WALK_DURATION_SECONDS    = 10L;

    private final String sessionId;
    private final String proposalId;
    private final String partnerId;
    private final String partnerName;
    private final String partnerAvatar;
    private final String hotspotName;
    private final double meetingPointLat;
    private final double meetingPointLng;
    private final String scheduledTime;
    private final Status status;        // Global derived status
    private final String scheduledEnd;
    private final String startedAt;     // Global: first activation
    private final String endedAt;       // Global: last termination

    // Per-participant activation timestamps
    private final String userAActivatedAt;
    private final String userBActivatedAt;

    // Per-participant independent lifecycle
    private final Status userAStatus;
    private final Status userBStatus;
    private final String userAEndedAt;
    private final String userBEndedAt;

    private final boolean isReviewed;
    private final boolean isCallerUserA;

    public WalkSession(
            String sessionId,
            String proposalId,
            String partnerId,
            String partnerName,
            String partnerAvatar,
            String hotspotName,
            double meetingPointLat,
            double meetingPointLng,
            String scheduledTime,
            Status status,
            String scheduledEnd,
            String startedAt,
            String endedAt,
            String userAActivatedAt,
            String userBActivatedAt,
            Status userAStatus,
            Status userBStatus,
            String userAEndedAt,
            String userBEndedAt,
            boolean isReviewed,
            boolean isCallerUserA) {
        this.sessionId        = sessionId;
        this.proposalId       = proposalId;
        this.partnerId        = partnerId;
        this.partnerName      = partnerName;
        this.partnerAvatar    = partnerAvatar;
        this.hotspotName      = hotspotName;
        this.meetingPointLat  = meetingPointLat;
        this.meetingPointLng  = meetingPointLng;
        this.scheduledTime    = scheduledTime;
        this.status           = status;
        this.scheduledEnd     = scheduledEnd;
        this.startedAt        = startedAt;
        this.endedAt          = endedAt;
        this.userAActivatedAt = userAActivatedAt;
        this.userBActivatedAt = userBActivatedAt;
        this.userAStatus      = userAStatus;
        this.userBStatus      = userBStatus;
        this.userAEndedAt     = userAEndedAt;
        this.userBEndedAt     = userBEndedAt;
        this.isReviewed       = isReviewed;
        this.isCallerUserA    = isCallerUserA;
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public String  getSessionId()         { return sessionId; }
    public String  getProposalId()        { return proposalId; }
    public String  getPartnerId()         { return partnerId; }
    public String  getPartnerName()       { return partnerName; }
    public String  getPartnerAvatar()     { return partnerAvatar; }
    public String  getHotspotName()       { return hotspotName; }
    public double  getMeetingPointLat()   { return meetingPointLat; }
    public double  getMeetingPointLng()   { return meetingPointLng; }
    public String  getScheduledTime()     { return scheduledTime; }
    public Status  getStatus()            { return status; }
    public String  getScheduledEnd()      { return scheduledEnd; }
    public String  getStartedAt()         { return startedAt; }
    public String  getEndedAt()           { return endedAt; }
    public String  getUserAActivatedAt()  { return userAActivatedAt; }
    public String  getUserBActivatedAt()  { return userBActivatedAt; }
    public Status  getUserAStatus()       { return userAStatus; }
    public Status  getUserBStatus()       { return userBStatus; }
    public String  getUserAEndedAt()      { return userAEndedAt; }
    public String  getUserBEndedAt()      { return userBEndedAt; }
    public boolean isReviewed()           { return isReviewed; }
    public boolean isCallerUserA()        { return isCallerUserA; }

    // ── Derived helpers used by the UI ────────────────────────────────────────

    /** The local user's own per-participant status. */
    public Status getCallerStatus() {
        return isCallerUserA ? userAStatus : userBStatus;
    }

    /** The remote partner's per-participant status. */
    public Status getPartnerStatus() {
        return isCallerUserA ? userBStatus : userAStatus;
    }

    /** True when the local user has already pressed Arrive. */
    public boolean hasCallerActivated() {
        return isCallerUserA ? userAActivatedAt != null : userBActivatedAt != null;
    }

    /** True when both participants have activated (legacy helper, kept for backward compat). */
    public boolean hasBothActivated() {
        return userAActivatedAt != null && userBActivatedAt != null;
    }

    /**
     * True when the local user has been individually ACTIVE long enough
     * to be eligible for completion (S-5 per-participant minimum).
     */
    public boolean canComplete() {
        String callerActivatedAt = isCallerUserA ? userAActivatedAt : userBActivatedAt;
        if (callerActivatedAt == null) return false;
        long startMs = Instant.parse(callerActivatedAt).toEpochMilli();
        long elapsed = System.currentTimeMillis() - startMs;
        return elapsed >= MINIMUM_WALK_DURATION_SECONDS * 1_000L;
    }
}
