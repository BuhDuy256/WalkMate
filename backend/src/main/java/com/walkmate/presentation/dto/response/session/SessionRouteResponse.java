package com.walkmate.presentation.dto.response.session;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Response DTO for GET /api/v1/sessions/{sessionId}/route.
 * Contains both participants' GPS polyline sequences and pre-computed walk stats.
 */
public record SessionRouteResponse(

        @JsonProperty("session_id")
        String sessionId,

        @JsonProperty("user_a_polylines")
        List<String> userAPolylines,

        @JsonProperty("user_b_polylines")
        List<String> userBPolylines,

        @JsonProperty("total_distance_km")
        double totalDistanceKm,

        @JsonProperty("duration_minutes")
        int durationMinutes
) {}
