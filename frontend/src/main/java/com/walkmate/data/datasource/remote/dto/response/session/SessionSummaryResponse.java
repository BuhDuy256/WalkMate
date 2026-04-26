package com.walkmate.data.datasource.remote.dto.response.session;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * DTO for GET /api/v1/sessions/history items.
 * Contains both participants with resolved names and individual walk stats.
 */
public class SessionSummaryResponse {

    @SerializedName("session_id")
    private String sessionId;

    @SerializedName("status")
    private String status;

    @SerializedName("scheduled_start")
    private String scheduledStart;

    @SerializedName("ended_at")
    private String endedAt;

    @SerializedName("is_reviewed")
    private boolean isReviewed;

    @SerializedName("participants")
    private List<ParticipantResponse> participants;

    public String getSessionId()                         { return sessionId; }
    public String getStatus()                            { return status; }
    public String getScheduledStart()                    { return scheduledStart; }
    public String getEndedAt()                           { return endedAt; }
    public boolean isReviewed()                          { return isReviewed; }
    public List<ParticipantResponse> getParticipants()   { return participants; }

    public static class ParticipantResponse {

        @SerializedName("participant_id")
        private String participantId;

        @SerializedName("full_name")
        private String fullName;

        @SerializedName("distance_km")
        private double distanceKm;

        @SerializedName("duration_minutes")
        private int durationMinutes;

        @SerializedName("user_status")
        private String userStatus;

        @SerializedName("avatar_url")
        private String avatarUrl;

        public String getParticipantId()   { return participantId; }
        public String getFullName()        { return fullName; }
        public double getDistanceKm()      { return distanceKm; }
        public int    getDurationMinutes() { return durationMinutes; }
        public String getUserStatus()      { return userStatus; }
        public String getAvatarUrl()       { return avatarUrl; }
    }
}
