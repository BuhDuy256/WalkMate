package com.walkmate.presentation.dto.request.walkpost;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateWalkPostRequest(
        @Size(max = 150) String caption,
        @NotBlank String visibility,
        boolean showCompanion,
        boolean showRouteMap,
        boolean showStats
) {}
