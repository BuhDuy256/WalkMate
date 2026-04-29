package com.walkmate.presentation.dto.response.session;

import com.fasterxml.jackson.annotation.JsonProperty;

public record WalkSessionResponse(

        @JsonProperty("session_id")
        String sessionId,

        @JsonProperty("proposal_id")
        String proposalId,

        @JsonProperty("user_id_a")
        String userIdA,

        @JsonProperty("user_id_b")
        String userIdB,

        @JsonProperty("hotspot_id")
        String hotspotId,

        @JsonProperty("hotspot_name")
        String hotspotName,

        // Derived from hotspot lat/lng via JOIN — retained for TrackingScreenActivity
        @JsonProperty("meeting_point_lat")
        double meetingPointLat,

        @JsonProperty("meeting_point_lng")
        double meetingPointLng,

        @JsonProperty("scheduled_start")
        String scheduledStart,

        @JsonProperty("scheduled_end")
        String scheduledEnd,

        @JsonProperty("status")
        String status,

        @JsonProperty("created_at")
        String createdAt,

        @JsonProperty("started_at")
        String startedAt,

        @JsonProperty("ended_at")
        String endedAt,

        @JsonProperty("user_a_activated_at")
        String userAActivatedAt,

        @JsonProperty("user_b_activated_at")
        String userBActivatedAt,

        @JsonProperty("user_a_status")
        String userAStatus,

        @JsonProperty("user_b_status")
        String userBStatus,

        @JsonProperty("user_a_ended_at")
        String userAEndedAt,

        @JsonProperty("user_b_ended_at")
        String userBEndedAt,

        @JsonProperty("user_a_distance_km")
        double userADistanceKm,

        @JsonProperty("user_a_duration_seconds")
        long userADurationSeconds,

        @JsonProperty("user_b_distance_km")
        double userBDistanceKm,

        @JsonProperty("user_b_duration_seconds")
        long userBDurationSeconds,

        @JsonProperty("cancellation_reason")
        String cancellationReason,

        @JsonProperty("partner_name")
        String partnerName,

        @JsonProperty("partner_avatar_url")
        String partnerAvatarUrl,

        @JsonProperty("is_reviewed")
        boolean isReviewed
) {}
