package com.walkmate.data.datasource.remote.dto.response.proposal;

import com.google.gson.annotations.SerializedName;

public class WalkProposalResponse {

    @SerializedName("proposal_id")
    private String proposalId;

    @SerializedName("callers_intent_id")
    private String callersIntentId;

    @SerializedName("matched_intent_id")
    private String matchedIntentId;

    @SerializedName("callers_user_id")
    private String callersUserId;

    @SerializedName("matched_user_id")
    private String matchedUserId;

    @SerializedName("matched_user_name")
    private String matchedUserName;

    @SerializedName("matched_user_avatar_url")
    private String matchedUserAvatarUrl;

    @SerializedName("matched_user_age")
    private int matchedUserAge;

    @SerializedName("matched_user_trust_score")
    private int matchedUserTrustScore;

    @SerializedName("overlapping_tags")
    private java.util.List<String> overlappingTags;

    @SerializedName("proposed_time_start")
    private String proposedTimeStart;

    @SerializedName("proposed_time_end")
    private String proposedTimeEnd;

    @SerializedName("hotspot_id")
    private String hotspotId;

    @SerializedName("hotspot_name")
    private String hotspotName;

    @SerializedName("status")
    private String status;

    @SerializedName("expires_at")
    private String expiresAt;

    @SerializedName("session_id")
    private String sessionId;

    @SerializedName("my_acceptance_status")
    private String myAcceptanceStatus;

    @SerializedName("is_private")
    private boolean privateInvite;

    public String getProposalId()       { return proposalId; }
    public String getCallersIntentId()  { return callersIntentId; }
    public String getMatchedIntentId()  { return matchedIntentId; }
    public String getCallersUserId()    { return callersUserId; }
    public String getMatchedUserId()    { return matchedUserId; }
    public String getMatchedUserName()      { return matchedUserName; }
    public String getMatchedUserAvatarUrl() { return matchedUserAvatarUrl; }
    public int    getMatchedUserAge()       { return matchedUserAge; }
    public int    getMatchedUserTrustScore() { return matchedUserTrustScore; }
    public java.util.List<String> getOverlappingTags() { return overlappingTags; }
    public String getProposedTimeStart() { return proposedTimeStart; }
    public String getProposedTimeEnd()   { return proposedTimeEnd; }
    public String getHotspotId()         { return hotspotId; }
    public String getHotspotName()       { return hotspotName; }
    public String getStatus()            { return status; }
    public String getExpiresAt()         { return expiresAt; }
    public String getSessionId()         { return sessionId; }
    public String getMyAcceptanceStatus(){ return myAcceptanceStatus; }
    public boolean isPrivateInvite()     { return privateInvite; }
}
