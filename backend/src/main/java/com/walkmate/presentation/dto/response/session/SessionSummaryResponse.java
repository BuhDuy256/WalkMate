package com.walkmate.presentation.dto.response.session;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Response DTO for GET /api/v1/sessions/history entries.
 *
 * {@code participants} contains one entry per walk partner (always two),
 * each carrying the participant's resolved full name and personal walk stats.
 *
 * {@code reviewSnapshot} and {@code reportSnapshot} are non-null only when the
 * caller has already reviewed or reported this session, allowing the UI to
 * prefill the form with previously submitted content.
 */
public record SessionSummaryResponse(

        @JsonProperty("session_id")
        String sessionId,

        @JsonProperty("status")
        String status,

        @JsonProperty("scheduled_start")
        String scheduledStart,

        @JsonProperty("ended_at")
        String endedAt,

        @JsonProperty("is_reviewed")
        boolean isReviewed,

        @JsonProperty("is_reported")
        boolean isReported,

        @JsonProperty("review_snapshot")
        ReviewSnapshot reviewSnapshot,

        @JsonProperty("report_snapshot")
        ReportSnapshot reportSnapshot,

        @JsonProperty("meeting_point_lat")
        double meetingPointLat,

        @JsonProperty("meeting_point_lng")
        double meetingPointLng,

        @JsonProperty("participants")
        List<ParticipantSummaryResponse> participants,

        @JsonProperty("caller_avatar_url")
        String callerAvatarUrl,

        @JsonProperty("hotspot_name")
        String hotspotName,

        @JsonProperty("current_user_personal_status")
        String currentUserPersonalStatus,

        @JsonProperty("partner_personal_status")
        String partnerPersonalStatus,

        @JsonProperty("current_user_has_posted")
        boolean currentUserHasPosted,

        @JsonProperty("current_user_post_id")
        String currentUserPostId,

        @JsonProperty("can_post")
        boolean canPost,

        @JsonProperty("can_review")
        boolean canReview,

        @JsonProperty("can_report")
        boolean canReport
) {
    public record ReviewSnapshot(
            @JsonProperty("rating_stars") int ratingStars,
            @JsonProperty("comment") String comment,
            @JsonProperty("tag_ids") List<String> tagIds
    ) {}

    public record ReportSnapshot(
            @JsonProperty("reason") String reason,
            @JsonProperty("evidence_url") String evidenceUrl
    ) {}
}
