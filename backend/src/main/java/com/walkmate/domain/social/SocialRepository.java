package com.walkmate.domain.social;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface SocialRepository {

    // ── Follow ────────────────────────────────────────────────────────────────

    void follow(UUID followerId, UUID followeeId);

    void unfollow(UUID followerId, UUID followeeId);

    boolean isFollowing(UUID followerId, UUID followeeId);

    List<UUID> getFollowerIds(UUID userId);

    List<UUID> getFolloweeIds(UUID userId);

    // ── Block ─────────────────────────────────────────────────────────────────

    void block(UUID blockerId, UUID blockedId);

    void unblock(UUID blockerId, UUID blockedId);

    boolean isBlocked(UUID blockerId, UUID blockedId);

    /**
     * Returns the union of:
     *   - all users that {@code userId} has explicitly blocked, AND
     *   - all users that have blocked {@code userId}.
     *
     * This single method executes a UNION query in one DB round-trip and is
     * the primary hook for the matching engine's block-exclusion filter.
     * See {@code RuleBasedMatchingStrategy} and {@code OPTIMIZATION_DECISION_LOG.md}.
     */
    Set<UUID> getBlockedAndBlockerIds(UUID userId);

    // ── Friendship ────────────────────────────────────────────────────────────

    /**
     * Returns true when a friendship row with status = 'ACCEPTED' exists
     * between the two users (in either direction).
     * Used to validate private WalkIntent creation (I-7).
     */
    boolean areAcceptedFriends(UUID userId1, UUID userId2);
}
