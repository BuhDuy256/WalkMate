package com.walkmate.presentation.dto.request.user;

import com.walkmate.domain.user.VisibilityMode;
import jakarta.validation.constraints.NotNull;

public record SetVisibilityRequest(
        @NotNull(message = "mode is required")
        VisibilityMode mode
) {
}
