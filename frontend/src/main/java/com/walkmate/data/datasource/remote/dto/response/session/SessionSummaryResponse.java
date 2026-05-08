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

    @SerializedName("is_reported")
    private boolean isReported;

    @SerializedName("meeting_point_lat")
    private double meetingPointLat;

    @SerializedName("meeting_point_lng")
    private double meetingPointLng;

    @SerializedName("participants")
    private List<ParticipantResponse> participants;

    @SerializedName("hotspot_name")
    private String hotspotName;

    @SerializedName("caller_avatar_url")
    private String callerAvatarUrl;

    @SerializedName("review_snapshot")
    private ReviewSnapshot reviewSnapshot;

    @SerializedName("report_snapshot")
    private ReportSnapshot reportSnapshot;

    @SerializedName("current_user_personal_status")
    private String currentUserPersonalStatus;

    @SerializedName("partner_personal_status")
    private String partnerPersonalStatus;

    @SerializedName("current_user_has_posted")
    private boolean currentUserHasPosted;

    @SerializedName("current_user_post_id")
    private String currentUserPostId;

    @SerializedName("can_post")
    private boolean canPost;

    @SerializedName("can_review")
    private boolean canReview;

    @SerializedName("can_report")
    private boolean canReport;

    public String getSessionId()                         { return sessionId; }
    public String getStatus()                            { return status; }
    public String getScheduledStart()                    { return scheduledStart; }
    public String getEndedAt()                           { return endedAt; }
    public boolean isReviewed()                          { return isReviewed; }
    public boolean isReported()                          { return isReported; }
    public double getMeetingPointLat()                   { return meetingPointLat; }
    public double getMeetingPointLng()                   { return meetingPointLng; }
    public List<ParticipantResponse> getParticipants()   { return participants; }
    public String getHotspotName()                       { return hotspotName; }
    public String getCallerAvatarUrl()                   { return callerAvatarUrl; }
    public ReviewSnapshot getReviewSnapshot()            { return reviewSnapshot; }
    public ReportSnapshot getReportSnapshot()            { return reportSnapshot; }
    public String getCurrentUserPersonalStatus()         { return currentUserPersonalStatus; }
    public String getPartnerPersonalStatus()             { return partnerPersonalStatus; }
    public boolean isCurrentUserHasPosted()              { return currentUserHasPosted; }
    public String getCurrentUserPostId()                 { return currentUserPostId; }
    public boolean isCanPost()                           { return canPost; }
    public boolean isCanReview()                         { return canReview; }
    public boolean isCanReport()                         { return canReport; }

    public static class ReviewSnapshot {
        @SerializedName("rating_stars") private int ratingStars;
        @SerializedName("comment")      private String comment;
        @SerializedName("tag_ids")      private List<String> tagIds;

        public int getRatingStars()    { return ratingStars; }
        public String getComment()     { return comment; }
        public List<String> getTagIds(){ return tagIds; }
    }

    public static class ReportSnapshot {
        @SerializedName("reason")       private String reason;
        @SerializedName("evidence_url") private String evidenceUrl;

        public String getReason()      { return reason; }
        public String getEvidenceUrl() { return evidenceUrl; }
    }

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
