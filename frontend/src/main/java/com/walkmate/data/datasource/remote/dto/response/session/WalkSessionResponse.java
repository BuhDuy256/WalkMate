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

    // ── Global session status ─────────────────────────────────────────────────
    @SerializedName("status")
    private String status;

    @SerializedName("created_at")
    private String createdAt;

    @SerializedName("started_at")
    private String startedAt;           // set on first participant activation

    @SerializedName("ended_at")
    private String endedAt;             // set when last participant terminates

    // ── Per-participant arrival timestamps ────────────────────────────────────
    @SerializedName("user_a_activated_at")
    private String userAActivatedAt;    // null until user A arrives

    @SerializedName("user_b_activated_at")
    private String userBActivatedAt;    // null until user B arrives

    // ── Per-participant independent lifecycle ─────────────────────────────────
    @SerializedName("user_a_status")
    private String userAStatus;         // PENDING | ACTIVE | COMPLETED | …

    @SerializedName("user_b_status")
    private String userBStatus;         // PENDING | ACTIVE | COMPLETED | …

    @SerializedName("user_a_ended_at")
    private String userAEndedAt;        // null until user A finishes

    @SerializedName("user_b_ended_at")
    private String userBEndedAt;        // null until user B finishes

    // ── Per-participant walk metrics ──────────────────────────────────────────
    @SerializedName("user_a_distance_km")
    private double userADistanceKm;

    @SerializedName("user_a_duration_seconds")
    private long userADurationSeconds;

    @SerializedName("user_b_distance_km")
    private double userBDistanceKm;

    @SerializedName("user_b_duration_seconds")
    private long userBDurationSeconds;

    // ── Cancellation / abort metadata ─────────────────────────────────────────
    @SerializedName("cancellation_reason")
    private String cancellationReason;  // null unless CANCELLED

    @SerializedName("abort_reason")
    private String abortReason;         // null unless ABORTED

    @SerializedName("is_reviewed")
    private boolean isReviewed;

    public String getSessionId()            { return sessionId; }
    public String getProposalId()           { return proposalId; }
    public String getUserIdA()              { return userIdA; }
    public String getUserIdB()              { return userIdB; }
    public double getMeetingPointLat()      { return meetingPointLat; }
    public double getMeetingPointLng()      { return meetingPointLng; }
    public String getScheduledStart()       { return scheduledStart; }
    public String getScheduledEnd()         { return scheduledEnd; }
    public String getStatus()               { return status; }
    public String getCreatedAt()            { return createdAt; }
    public String getStartedAt()            { return startedAt; }
    public String getEndedAt()              { return endedAt; }
    public String getUserAActivatedAt()     { return userAActivatedAt; }
    public String getUserBActivatedAt()     { return userBActivatedAt; }
    public String getUserAStatus()          { return userAStatus; }
    public String getUserBStatus()          { return userBStatus; }
    public String getUserAEndedAt()         { return userAEndedAt; }
    public String getUserBEndedAt()         { return userBEndedAt; }
    public double getUserADistanceKm()      { return userADistanceKm; }
    public long   getUserADurationSeconds() { return userADurationSeconds; }
    public double getUserBDistanceKm()      { return userBDistanceKm; }
    public long   getUserBDurationSeconds() { return userBDurationSeconds; }
    public String getCancellationReason()   { return cancellationReason; }
    public String getAbortReason()          { return abortReason; }
    public boolean isReviewed()             { return isReviewed; }
}
