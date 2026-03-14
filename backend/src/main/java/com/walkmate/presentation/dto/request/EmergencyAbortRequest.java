package com.walkmate.presentation.dto.request;

import com.walkmate.domain.session.enums.AbortReason;
import jakarta.validation.constraints.NotNull;

public record EmergencyAbortRequest(
    @NotNull AbortReason reason,
    String details // Optional text context
) {}
