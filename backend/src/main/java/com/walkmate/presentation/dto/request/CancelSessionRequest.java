package com.walkmate.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CancelSessionRequest(
    @NotBlank String reason) {
}
