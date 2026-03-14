package com.walkmate.presentation.dto.response;

import java.util.UUID;

public record SessionTrackingResponse(
    UUID sessionId,
    int appendedPoints,
    String message) {
}
