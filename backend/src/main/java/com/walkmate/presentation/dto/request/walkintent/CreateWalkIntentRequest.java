package com.walkmate.presentation.dto.request.walkintent;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record CreateWalkIntentRequest(
        @NotBlank(message = "Hotspot ID is required")
        @JsonProperty("hotspot_id")
        String hotspotId,

        @NotNull(message = "Time window start is required")
        @JsonProperty("time_window_start")
        Instant timeWindowStart,

        @NotNull(message = "Time window end is required")
        @JsonProperty("time_window_end")
        Instant timeWindowEnd,

        @Min(value = 0, message = "Age min must be >= 0")
        @JsonProperty("age_min")
        int ageMin,

        @Min(value = 0, message = "Age max must be >= 0")
        @JsonProperty("age_max")
        int ageMax
) {
}
