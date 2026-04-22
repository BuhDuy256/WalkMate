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

    @SerializedName("proposed_time_start")
    private String proposedTimeStart;   // ISO-8601

    @SerializedName("proposed_time_end")
    private String proposedTimeEnd;     // ISO-8601

    @SerializedName("proposed_lat")
    private double proposedLat;

    @SerializedName("proposed_lng")
    private double proposedLng;

    @SerializedName("status")
    private String status;

    @SerializedName("expires_at")
    private String expiresAt;

    @SerializedName("session_id")
    private String sessionId;           // null until CONFIRMED

    // Populated on every response to /accept: "ACCEPTED" if this user has
    // already tapped Accept, null/absent otherwise.  Distinguishes Case A
    // (partial acceptance, partner still pending) from a fresh PENDING view.
    @SerializedName("my_acceptance_status")
    private String myAcceptanceStatus;

    // True when this proposal was created via a private walk invite (UC-15).
    // The backend auto-accepts the sender's side for private invites.
    @SerializedName("is_private")
    private boolean privateInvite;

    public String getProposalId()       { return proposalId; }
    public String getCallersIntentId()  { return callersIntentId; }
    public String getMatchedIntentId()  { return matchedIntentId; }
    public String getCallersUserId()    { return callersUserId; }
    public String getMatchedUserId()    { return matchedUserId; }
    public String getMatchedUserName()  { return matchedUserName; }
    public String getProposedTimeStart(){ return proposedTimeStart; }
    public String getProposedTimeEnd()  { return proposedTimeEnd; }
    public double getProposedLat()      { return proposedLat; }
    public double getProposedLng()      { return proposedLng; }
    public String getStatus()             { return status; }
    public String getExpiresAt()          { return expiresAt; }
    public String getSessionId()          { return sessionId; }
    public String getMyAcceptanceStatus() { return myAcceptanceStatus; }
    public boolean isPrivateInvite()       { return privateInvite; }
}
