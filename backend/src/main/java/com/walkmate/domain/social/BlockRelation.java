package com.walkmate.domain.social;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable value object representing a directional block relationship.
 * {@code blockerId} has blocked {@code blockedId}.
 *
 * A block is always mutual from the matching engine's perspective:
 * if A blocks B, neither A nor B should be matched with each other.
 */
public record BlockRelation(UUID blockerId, UUID blockedId, Instant blockedAt) {

    public static BlockRelation create(UUID blockerId, UUID blockedId) {
        return new BlockRelation(blockerId, blockedId, Instant.now());
    }
}
