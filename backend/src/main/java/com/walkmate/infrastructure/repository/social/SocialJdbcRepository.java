package com.walkmate.infrastructure.repository.social;

import com.walkmate.domain.social.SocialRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class SocialJdbcRepository implements SocialRepository {

    private final JdbcClient jdbcClient;

    // ── Follow ────────────────────────────────────────────────────────────────

    @Override
    public void follow(UUID followerId, UUID followeeId) {
        jdbcClient.sql("""
                        INSERT INTO follow_relation (follower_id, followee_id)
                        VALUES (:followerId, :followeeId)
                        ON CONFLICT DO NOTHING
                        """)
                .param("followerId", followerId)
                .param("followeeId", followeeId)
                .update();
    }

    @Override
    public void unfollow(UUID followerId, UUID followeeId) {
        jdbcClient.sql("""
                        DELETE FROM follow_relation
                        WHERE follower_id = :followerId AND followee_id = :followeeId
                        """)
                .param("followerId", followerId)
                .param("followeeId", followeeId)
                .update();
    }

    @Override
    public boolean isFollowing(UUID followerId, UUID followeeId) {
        return jdbcClient.sql("""
                        SELECT COUNT(1) FROM follow_relation
                        WHERE follower_id = :followerId AND followee_id = :followeeId
                        """)
                .param("followerId", followerId)
                .param("followeeId", followeeId)
                .query(Integer.class)
                .single() > 0;
    }

    @Override
    public List<UUID> getFollowerIds(UUID userId) {
        return jdbcClient.sql("""
                        SELECT follower_id FROM follow_relation
                        WHERE followee_id = :userId
                        ORDER BY followed_at DESC
                        """)
                .param("userId", userId)
                .query(UUID.class)
                .list();
    }

    @Override
    public List<UUID> getFolloweeIds(UUID userId) {
        return jdbcClient.sql("""
                        SELECT followee_id FROM follow_relation
                        WHERE follower_id = :userId
                        ORDER BY followed_at DESC
                        """)
                .param("userId", userId)
                .query(UUID.class)
                .list();
    }

    // ── Block ─────────────────────────────────────────────────────────────────

    @Override
    public void block(UUID blockerId, UUID blockedId) {
        jdbcClient.sql("""
                        INSERT INTO block_relation (blocker_id, blocked_id)
                        VALUES (:blockerId, :blockedId)
                        ON CONFLICT DO NOTHING
                        """)
                .param("blockerId", blockerId)
                .param("blockedId", blockedId)
                .update();
    }

    @Override
    public void unblock(UUID blockerId, UUID blockedId) {
        jdbcClient.sql("""
                        DELETE FROM block_relation
                        WHERE blocker_id = :blockerId AND blocked_id = :blockedId
                        """)
                .param("blockerId", blockerId)
                .param("blockedId", blockedId)
                .update();
    }

    @Override
    public boolean isBlocked(UUID blockerId, UUID blockedId) {
        return jdbcClient.sql("""
                        SELECT COUNT(1) FROM block_relation
                        WHERE blocker_id = :blockerId AND blocked_id = :blockedId
                        """)
                .param("blockerId", blockerId)
                .param("blockedId", blockedId)
                .query(Integer.class)
                .single() > 0;
    }

    /**
     * Fetches the full mutual-block exclusion set in a single UNION query.
     *
     * Query plan:
     *   - Arm 1: index scan on idx_block_blocker (blocker_id = userId)
     *   - Arm 2: index scan on idx_block_blocked (blocked_id = userId)
     *   - UNION deduplicates; cost is O(k) where k = number of block relationships.
     *
     * The matching engine calls this once per findMatch request and filters
     * candidates in-memory — O(1) DB round-trips regardless of candidate count.
     */
    @Override
    public Set<UUID> getBlockedAndBlockerIds(UUID userId) {
        List<UUID> ids = jdbcClient.sql("""
                        SELECT blocked_id  AS user_id FROM block_relation WHERE blocker_id = :userId
                        UNION
                        SELECT blocker_id  AS user_id FROM block_relation WHERE blocked_id = :userId
                        """)
                .param("userId", userId)
                .query(UUID.class)
                .list();
        return new HashSet<>(ids);
    }

    // ── Friendship ────────────────────────────────────────────────────────────

    @Override
    public boolean areAcceptedFriends(UUID userId1, UUID userId2) {
        Integer count = jdbcClient.sql("""
                        SELECT COUNT(1) FROM friendship
                        WHERE status = 'ACCEPTED'
                          AND ((requester_id = :userId1 AND addressee_id = :userId2)
                            OR (requester_id = :userId2 AND addressee_id = :userId1))
                        """)
                .param("userId1", userId1)
                .param("userId2", userId2)
                .query(Integer.class)
                .single();
        return count != null && count > 0;
    }
}
