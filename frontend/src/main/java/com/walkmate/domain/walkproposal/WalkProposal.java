package com.walkmate.domain.walkproposal;

import java.util.List;

public class WalkProposal {

    public enum Status {
        PENDING, CONFIRMED, REJECTED
    }

    private final String proposalId;
    private final String intentId;
    private final String matchedUserId;
    private final String matchedUserName;
    private final int matchedUserAge;
    private final int trustScore;
    private final List<String> overlappingTags;
    private final float overlappingTimeStart; // float hour 0–24, e.g. 16.5 = 16:30
    private final float overlappingTimeEnd;
    private final Status status;
    private final String expiresAt;          // ISO-8601
    private final double meetingLat;
    private final double meetingLng;
    private final String myAcceptanceStatus; // "ACCEPTED" or null
    private final String sessionId;          // null until CONFIRMED

    public WalkProposal(
            String proposalId,
            String intentId,
            String matchedUserId,
            String matchedUserName,
            int matchedUserAge,
            int trustScore,
            List<String> overlappingTags,
            float overlappingTimeStart,
            float overlappingTimeEnd,
            Status status,
            String expiresAt,
            double meetingLat,
            double meetingLng,
            String myAcceptanceStatus,
            String sessionId) {
        this.proposalId = proposalId;
        this.intentId = intentId;
        this.matchedUserId = matchedUserId;
        this.matchedUserName = matchedUserName;
        this.matchedUserAge = matchedUserAge;
        this.trustScore = trustScore;
        this.overlappingTags = overlappingTags;
        this.overlappingTimeStart = overlappingTimeStart;
        this.overlappingTimeEnd = overlappingTimeEnd;
        this.status = status;
        this.expiresAt = expiresAt;
        this.meetingLat = meetingLat;
        this.meetingLng = meetingLng;
        this.myAcceptanceStatus = myAcceptanceStatus;
        this.sessionId = sessionId;
    }

    public String getProposalId() { return proposalId; }
    public String getIntentId() { return intentId; }
    public String getMatchedUserId() { return matchedUserId; }
    public String getMatchedUserName() { return matchedUserName; }
    public int getMatchedUserAge() { return matchedUserAge; }
    public int getTrustScore() { return trustScore; }
    public List<String> getOverlappingTags() { return overlappingTags; }
    public float getOverlappingTimeStart() { return overlappingTimeStart; }
    public float getOverlappingTimeEnd() { return overlappingTimeEnd; }
    public Status getStatus() { return status; }
    public String getExpiresAt() { return expiresAt; }
    public double getMeetingLat() { return meetingLat; }
    public double getMeetingLng() { return meetingLng; }
    public String getMyAcceptanceStatus() { return myAcceptanceStatus; }
    public String getSessionId() { return sessionId; }

    public boolean isAcceptedByMe() { return "ACCEPTED".equals(myAcceptanceStatus); }
    public boolean isConfirmed() { return Status.CONFIRMED == status && sessionId != null; }

    /** Returns a copy of this proposal with the given enriched display name. */
    public WalkProposal withMatchedUserName(String enrichedName) {
        return new WalkProposal(proposalId, intentId, matchedUserId, enrichedName,
                matchedUserAge, trustScore, overlappingTags, overlappingTimeStart,
                overlappingTimeEnd, status, expiresAt, meetingLat, meetingLng,
                myAcceptanceStatus, sessionId);
    }
}
