package com.walkmate.presentation.dto.request;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public record CreateTestSessionRequest(
        @NotNull UUID user1Id,
        @NotNull UUID user2Id,
        @NotNull Instant scheduledStartTime,
        @NotNull Instant scheduledEndTime,
        @NotNull Boolean mutualConfirmation) {
}
