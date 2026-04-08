package com.walkmate.domain.walksession;

import java.time.Instant;

public class WalkSession {

    public enum Status {
        PENDING, ACTIVE, CANCELLED, COMPLETED, NO_SHOW, ABORTED
    }

    public static final int ACTIVATION_WINDOW_BEFORE_MINUTES = 10;
    public static final int ACTIVATION_WINDOW_AFTER_MINUTES  = 15;
    public static final int MINIMUM_WALK_DURATION_MINUTES    = 5;

    private final String sessionId;
    private final String proposalId;
    private final String partnerName;
    private final String partnerAvatar; // URL or null
    private final double meetingPointLat;
    private final double meetingPointLng;
    private final String scheduledTime; // ISO-8601 string
    private final Status status;
    private final String scheduledEnd;
    private final String startedAt;
    private final String endedAt;
    private final String userAActivatedAt;
    private final String userBActivatedAt;
    private final boolean isReviewed;
    private final boolean isCallerUserA;

    public WalkSession(
            String sessionId,
            String proposalId,
            String partnerName,
            String partnerAvatar,
            double meetingPointLat,
            double meetingPointLng,
            String scheduledTime,
            Status status,
            String scheduledEnd,
            String startedAt,
            String endedAt,
            String userAActivatedAt,
            String userBActivatedAt,
            boolean isReviewed,
            boolean isCallerUserA) {
        this.sessionId = sessionId;
        this.proposalId = proposalId;
        this.partnerName = partnerName;
        this.partnerAvatar = partnerAvatar;
        this.meetingPointLat = meetingPointLat;
        this.meetingPointLng = meetingPointLng;
        this.scheduledTime = scheduledTime;
        this.status = status;
        this.scheduledEnd = scheduledEnd;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        this.userAActivatedAt = userAActivatedAt;
        this.userBActivatedAt = userBActivatedAt;
        this.isReviewed = isReviewed;
        this.isCallerUserA = isCallerUserA;
    }

    public String getSessionId() { return sessionId; }
    public String getProposalId() { return proposalId; }
    public String getPartnerName() { return partnerName; }
    public String getPartnerAvatar() { return partnerAvatar; }
    public double getMeetingPointLat() { return meetingPointLat; }
    public double getMeetingPointLng() { return meetingPointLng; }
    public String getScheduledTime() { return scheduledTime; }
    public Status getStatus() { return status; }
    public String getScheduledEnd() { return scheduledEnd; }
    public String getStartedAt() { return startedAt; }
    public String getEndedAt() { return endedAt; }
    public String getUserAActivatedAt() { return userAActivatedAt; }
    public String getUserBActivatedAt() { return userBActivatedAt; }
    public boolean isReviewed() { return isReviewed; }
    public boolean isCallerUserA() { return isCallerUserA; }

    public boolean hasCallerActivated() {
        return isCallerUserA ? userAActivatedAt != null : userBActivatedAt != null;
    }

    public boolean hasBothActivated() {
        return userAActivatedAt != null && userBActivatedAt != null;
    }

    public boolean canComplete() {
        if (startedAt == null) return false;
        long startMs = Instant.parse(startedAt).toEpochMilli();
        long elapsed = System.currentTimeMillis() - startMs;
        return elapsed >= MINIMUM_WALK_DURATION_MINUTES * 60_000L;
    }
}
