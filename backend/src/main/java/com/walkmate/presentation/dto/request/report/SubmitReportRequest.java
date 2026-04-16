package com.walkmate.presentation.dto.request.report;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SubmitReportRequest(

        @NotNull(message = "reportedUserId is required")
        @JsonProperty("reportedUserId")
        String reportedUserId,

        @NotBlank(message = "reason is required")
        @JsonProperty("reason")
        String reason,

        @JsonProperty("evidenceUrl")
        String evidenceUrl
) {}
