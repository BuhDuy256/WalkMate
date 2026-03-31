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
            Status status) {
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
}
