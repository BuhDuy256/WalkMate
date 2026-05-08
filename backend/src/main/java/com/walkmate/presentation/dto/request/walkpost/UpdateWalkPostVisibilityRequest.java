package com.walkmate.presentation.dto.request.walkpost;

import jakarta.validation.constraints.NotBlank;

public record UpdateWalkPostVisibilityRequest(
        @NotBlank String visibility
) {}
