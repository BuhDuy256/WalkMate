package com.walkmate.presentation.dto.response;

import com.walkmate.domain.session.SessionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record SessionResponse(
    UUID sessionId,
    UUID user1Id,
    UUID user2Id,
    SessionStatus status,
    Instant scheduledStartTime,
    Instant scheduledEndTime,
    Instant actualStartTime,
    Instant actualEndTime,
    BigDecimal totalDistance,
    long totalDuration,
    long version) {
}
