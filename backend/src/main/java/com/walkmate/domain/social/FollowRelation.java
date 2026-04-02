package com.walkmate.domain.social;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable value object representing a directional follow relationship.
 * {@code followerId} follows {@code followeeId}.
 */
public record FollowRelation(UUID followerId, UUID followeeId, Instant followedAt) {

    public static FollowRelation create(UUID followerId, UUID followeeId) {
        return new FollowRelation(followerId, followeeId, Instant.now());
    }
}
