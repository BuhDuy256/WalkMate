package com.walkmate.presentation.dto.response.session;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response DTO for GET /api/v1/sessions/history entries.
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
        int durationMinutes
) {}
