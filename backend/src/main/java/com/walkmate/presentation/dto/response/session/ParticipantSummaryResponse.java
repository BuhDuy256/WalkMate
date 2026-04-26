package com.walkmate.presentation.dto.response.session;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ParticipantSummaryResponse(

        @JsonProperty("participant_id")
        String participantId,

        @JsonProperty("full_name")
        String fullName,

        @JsonProperty("avatar_url")
        String avatarUrl,

        @JsonProperty("distance_km")
        double distanceKm,

        @JsonProperty("duration_minutes")
        int durationMinutes,

        @JsonProperty("user_status")
        String userStatus
) {}
