package com.walkmate.presentation.dto.request;

import com.walkmate.domain.session.AbortReason;
import jakarta.validation.constraints.NotNull;

public record AbortSessionRequest(
    @NotNull AbortReason reason) {
}
