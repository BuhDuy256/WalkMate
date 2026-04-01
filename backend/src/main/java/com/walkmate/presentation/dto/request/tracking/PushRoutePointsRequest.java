package com.walkmate.presentation.dto.request.tracking;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record PushRoutePointsRequest(

        @NotBlank(message = "session_id is required")
        @JsonProperty("session_id")
        String sessionId,

        @NotEmpty(message = "points must not be empty")
        @Valid
        @JsonProperty("points")
        List<RoutePointPayload> points
) {
    public record RoutePointPayload(

            @JsonProperty("local_id")
            long localId,

            @DecimalMin(value = "-90.0",  message = "lat must be >= -90")
            @DecimalMax(value = "90.0",   message = "lat must be <= 90")
            @JsonProperty("lat")
            double lat,

            @DecimalMin(value = "-180.0", message = "lng must be >= -180")
            @DecimalMax(value = "180.0",  message = "lng must be <= 180")
            @JsonProperty("lng")
            double lng,

            @Positive(message = "timestamp must be a positive epoch-ms value")
            @JsonProperty("timestamp")
            long timestamp,

            @JsonProperty("accuracy")
            float accuracy
    ) {}
}
