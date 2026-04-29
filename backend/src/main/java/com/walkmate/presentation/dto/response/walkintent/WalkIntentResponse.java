package com.walkmate.presentation.dto.response.walkintent;

import com.fasterxml.jackson.annotation.JsonProperty;

public record WalkIntentResponse(
        String id,

        @JsonProperty("hotspot_id")
        String hotspotId,

        @JsonProperty("hotspot_name")
        String hotspotName,

        @JsonProperty("user_id")
        String userId,

        @JsonProperty("time_window_start")
        String timeWindowStart,

        @JsonProperty("time_window_end")
        String timeWindowEnd,

        @JsonProperty("age_min")
        int ageMin,

        @JsonProperty("age_max")
        int ageMax,

        @JsonProperty("preferred_gender")
        String preferredGender,

        String status,

        @JsonProperty("created_at")
        String createdAt,

        @JsonProperty("expires_at")
        String expiresAt,

        @JsonProperty("is_private")
        boolean isPrivate,

        String description
) {
}
