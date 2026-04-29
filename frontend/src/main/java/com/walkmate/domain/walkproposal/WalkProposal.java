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
    private final String matchedUserAvatarUrl;
    private final int matchedUserAge;
    private final int trustScore;
    private final List<String> overlappingTags;
    private final float overlappingTimeStart; // float hour 0–24, e.g. 16.5 = 16:30
    private final float overlappingTimeEnd;
    private final Status status;
    private final String expiresAt;          // ISO-8601
    private final String hotspotId;
    private final String hotspotName;        // for display in the Proposal card and grouping
    private final String myAcceptanceStatus; // "ACCEPTED" or null
    private final String sessionId;          // null until CONFIRMED
    private final boolean isPrivateInvite;

    public WalkProposal(
            String proposalId,
            String intentId,
            String matchedUserId,
            String matchedUserName,
            String matchedUserAvatarUrl,
            int matchedUserAge,
            int trustScore,
            List<String> overlappingTags,
            float overlappingTimeStart,
            float overlappingTimeEnd,
            Status status,
            String expiresAt,
            String hotspotId,
            String hotspotName,
            String myAcceptanceStatus,
            String sessionId,
            boolean isPrivateInvite) {
        this.proposalId = proposalId;
        this.intentId = intentId;
        this.matchedUserId = matchedUserId;
        this.matchedUserName = matchedUserName;
        this.matchedUserAvatarUrl = matchedUserAvatarUrl;
        this.matchedUserAge = matchedUserAge;
        this.trustScore = trustScore;
        this.overlappingTags = overlappingTags;
        this.overlappingTimeStart = overlappingTimeStart;
        this.overlappingTimeEnd = overlappingTimeEnd;
        this.status = status;
        this.expiresAt = expiresAt;
        this.hotspotId = hotspotId;
        this.hotspotName = hotspotName;
        this.myAcceptanceStatus = myAcceptanceStatus;
        this.sessionId = sessionId;
        this.isPrivateInvite = isPrivateInvite;
    }

    public String getProposalId()            { return proposalId; }
    public String getIntentId()              { return intentId; }
    public String getMatchedUserId()            { return matchedUserId; }
    public String getMatchedUserName()          { return matchedUserName; }
    public String getMatchedUserAvatarUrl()     { return matchedUserAvatarUrl; }
    public int    getMatchedUserAge()           { return matchedUserAge; }
    public int    getTrustScore()            { return trustScore; }
    public List<String> getOverlappingTags() { return overlappingTags; }
    public float  getOverlappingTimeStart()  { return overlappingTimeStart; }
    public float  getOverlappingTimeEnd()    { return overlappingTimeEnd; }
    public Status getStatus()                { return status; }
    public String getExpiresAt()             { return expiresAt; }
    public String getHotspotId()             { return hotspotId; }
    public String getHotspotName()           { return hotspotName; }
    public String getMyAcceptanceStatus()    { return myAcceptanceStatus; }
    public String getSessionId()             { return sessionId; }

    public boolean isAcceptedByMe()       { return "ACCEPTED".equals(myAcceptanceStatus); }
    public boolean isCurrentUserAccepted(){ return isAcceptedByMe(); }
    public boolean isConfirmed()          { return Status.CONFIRMED == status && sessionId != null; }
    public boolean isPrivateInvite()      { return isPrivateInvite; }
}
