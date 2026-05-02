package com.walkmate.presentation.dto.request.report;

import jakarta.validation.constraints.NotBlank;

public record ResolveReportRequest(
    @NotBlank String resolution,
    String resolutionNote
) {}
