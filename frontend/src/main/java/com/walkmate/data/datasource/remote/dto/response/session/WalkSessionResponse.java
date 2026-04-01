package com.walkmate.data.datasource.remote.dto.response.session;

import com.google.gson.annotations.SerializedName;

public class WalkSessionResponse {

    @SerializedName("session_id")
    private String sessionId;

    @SerializedName("proposal_id")
    private String proposalId;

    @SerializedName("user_id_a")
    private String userIdA;

    @SerializedName("user_id_b")
    private String userIdB;

    @SerializedName("meeting_point_lat")
    private double meetingPointLat;

    @SerializedName("meeting_point_lng")
    private double meetingPointLng;

    @SerializedName("scheduled_start")
    private String scheduledStart;      // ISO-8601

    @SerializedName("scheduled_end")
    private String scheduledEnd;        // ISO-8601

    @SerializedName("status")
    private String status;

    @SerializedName("created_at")
    private String createdAt;

    @SerializedName("started_at")
    private String startedAt;           // null until ACTIVE

    @SerializedName("ended_at")
    private String endedAt;             // null until terminal

    @SerializedName("user_a_activated_at")
    private String userAActivatedAt;    // null until user A arrives

    @SerializedName("user_b_activated_at")
    private String userBActivatedAt;    // null until user B arrives

    @SerializedName("cancellation_reason")
    private String cancellationReason;  // null unless CANCELLED

    @SerializedName("abort_reason")
    private String abortReason;         // null unless ABORTED

    public String getSessionId()           { return sessionId; }
    public String getProposalId()          { return proposalId; }
    public String getUserIdA()             { return userIdA; }
    public String getUserIdB()             { return userIdB; }
    public double getMeetingPointLat()     { return meetingPointLat; }
    public double getMeetingPointLng()     { return meetingPointLng; }
    public String getScheduledStart()      { return scheduledStart; }
    public String getScheduledEnd()        { return scheduledEnd; }
    public String getStatus()              { return status; }
    public String getCreatedAt()           { return createdAt; }
    public String getStartedAt()           { return startedAt; }
    public String getEndedAt()             { return endedAt; }
    public String getUserAActivatedAt()    { return userAActivatedAt; }
    public String getUserBActivatedAt()    { return userBActivatedAt; }
    public String getCancellationReason()  { return cancellationReason; }
    public String getAbortReason()         { return abortReason; }
}
