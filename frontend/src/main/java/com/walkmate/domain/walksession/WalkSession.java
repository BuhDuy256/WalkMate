package com.walkmate.domain.walksession;

public class WalkSession {

    public enum Status {
        PENDING, ACTIVE, CANCELLED, COMPLETED, NO_SHOW, ABORTED
    }

    private final String sessionId;
    private final String proposalId;
    private final String partnerName;
    private final String partnerAvatar; // URL or null
    private final double meetingPointLat;
    private final double meetingPointLng;
    private final String scheduledTime; // ISO-8601 string
    private final Status status;

    public WalkSession(
            String sessionId,
            String proposalId,
            String partnerName,
            String partnerAvatar,
            double meetingPointLat,
            double meetingPointLng,
            String scheduledTime,
            Status status) {
        this.sessionId = sessionId;
        this.proposalId = proposalId;
        this.partnerName = partnerName;
        this.partnerAvatar = partnerAvatar;
        this.meetingPointLat = meetingPointLat;
        this.meetingPointLng = meetingPointLng;
        this.scheduledTime = scheduledTime;
        this.status = status;
    }

    public String getSessionId() { return sessionId; }
    public String getProposalId() { return proposalId; }
    public String getPartnerName() { return partnerName; }
    public String getPartnerAvatar() { return partnerAvatar; }
    public double getMeetingPointLat() { return meetingPointLat; }
    public double getMeetingPointLng() { return meetingPointLng; }
    public String getScheduledTime() { return scheduledTime; }
    public Status getStatus() { return status; }
}
