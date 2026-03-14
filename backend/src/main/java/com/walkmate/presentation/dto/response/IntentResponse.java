package com.walkmate.presentation.dto.response;

import com.walkmate.domain.intent.IntentStatus;
import com.walkmate.domain.intent.WalkPurpose;
import com.walkmate.domain.valueobject.MatchingConstraints;

import java.time.Instant;
import java.util.UUID;

public record IntentResponse(
    UUID intentId,
    UUID userId,
    double lat,
    double lng,
    Instant startTime,
    Instant endTime,
    WalkPurpose purpose,
    MatchingConstraints matchingConstraints,
    IntentStatus status,
    Instant createdAt,
    Instant expiresAt
) {}
