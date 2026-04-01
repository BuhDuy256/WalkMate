package com.walkmate.presentation.dto.request.session;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record CancelWalkSessionRequest(

        @NotBlank(message = "Cancellation reason is required")
        @JsonProperty("reason")
        String reason
) {}
