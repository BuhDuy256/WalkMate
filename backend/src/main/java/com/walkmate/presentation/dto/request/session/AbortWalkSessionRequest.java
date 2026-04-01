package com.walkmate.presentation.dto.request.session;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.walkmate.domain.session.AbortReason;
import jakarta.validation.constraints.NotNull;

public record AbortWalkSessionRequest(

        @NotNull(message = "Abort reason is required")
        @JsonProperty("reason")
        AbortReason reason
) {}
