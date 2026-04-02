package com.walkmate.application.user;

import java.util.UUID;

/**
 * Command carrying the caller's identity and the new FCM token.
 * Pure Java record — no framework annotations, safe to pass across layers.
 */
public record UpdateFcmTokenCommand(UUID userId, String token) {}
