package com.walkmate.presentation.dto.response.session;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response DTO for GET /api/v1/sessions/history entries.
 *
 * isReviewed indicates whether the authenticated caller has already submitted
 * a review for this session. The UI uses this to show or hide the "Leave a Review"
 * button on each history card without an extra round-trip.
 */
public record SessionSummaryResponse(

        @JsonProperty("session_id")
        String sessionId,

        @JsonProperty("status")
        String status,

        @JsonProperty("partner_id")
        String partnerId,

        @JsonProperty("scheduled_start")
        String scheduledStart,

        @JsonProperty("scheduled_end")
        String scheduledEnd,

        @JsonProperty("total_distance_km")
        double totalDistanceKm,

        @JsonProperty("duration_minutes")
        int durationMinutes,

        @JsonProperty("is_reviewed")
        boolean isReviewed
) {}
