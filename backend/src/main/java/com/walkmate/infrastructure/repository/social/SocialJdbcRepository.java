package com.walkmate.infrastructure.repository.social;

import com.walkmate.domain.social.Friendship;
import com.walkmate.domain.social.SocialRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class SocialJdbcRepository implements SocialRepository {

    private final JdbcClient jdbcClient;

    // ── Follow (remapped to friendship table — V104) ──────────────────────────
    // V104 replaced the unidirectional follow_relation with the bidirectional
    // friendship table. Follow semantics are preserved:
    //   follow(A,B)      → INSERT a PENDING friendship request from A to B
    //   unfollow(A,B)    → DELETE the friendship row where A is the requester
    //   isFollowing(A,B) → true if A has a PENDING or ACCEPTED friendship to B
    //   getFollowerIds   → requester_ids of PENDING/ACCEPTED rows addressed to userId
    //   getFolloweeIds   → addressee_ids of PENDING/ACCEPTED rows initiated by userId

    @Override
    public void follow(UUID followerId, UUID followeeId) {
        jdbcClient.sql("""
                        INSERT INTO friendship (requester_id, addressee_id, status)
                        VALUES (:followerId, :followeeId, CAST('PENDING' AS friend_status))
                        ON CONFLICT (requester_id, addressee_id) DO NOTHING
                        """)
                .param("followerId", followerId)
                .param("followeeId", followeeId)
                .update();
    }

    @Override
    public void unfollow(UUID followerId, UUID followeeId) {
        jdbcClient.sql("""
                        DELETE FROM friendship
                        WHERE requester_id = :followerId AND addressee_id = :followeeId
                        """)
                .param("followerId", followerId)
                .param("followeeId", followeeId)
                .update();
    }

    @Override
    public boolean isFollowing(UUID followerId, UUID followeeId) {
        Integer count = jdbcClient.sql("""
                        SELECT COUNT(1) FROM friendship
                        WHERE requester_id = :followerId
                          AND addressee_id = :followeeId
                          AND status IN ('PENDING', 'ACCEPTED')
                        """)
                .param("followerId", followerId)
                .param("followeeId", followeeId)
                .query(Integer.class)
                .single();
        return count != null && count > 0;
    }

    @Override
    public List<UUID> getFollowerIds(UUID userId) {
        return jdbcClient.sql("""
                        SELECT requester_id FROM friendship
                        WHERE addressee_id = :userId
                          AND status IN ('PENDING', 'ACCEPTED')
                        """)
                .param("userId", userId)
                .query(UUID.class)
                .list();
    }

    @Override
    public List<UUID> getFolloweeIds(UUID userId) {
        return jdbcClient.sql("""
                        SELECT addressee_id FROM friendship
                        WHERE requester_id = :userId
                          AND status IN ('PENDING', 'ACCEPTED')
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

    private static final org.springframework.jdbc.core.RowMapper<Friendship> FRIENDSHIP_MAPPER =
            (rs, rowNum) -> new Friendship(
                    rs.getString("friendship_id"),
                    UUID.fromString(rs.getString("requester_id")),
                    UUID.fromString(rs.getString("addressee_id")),
                    rs.getString("status"),
                    rs.getLong("version"),
                    rs.getTimestamp("created_at").toInstant(),
                    rs.getTimestamp("updated_at").toInstant()
            );

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

    @Override
    public void saveFriendship(Friendship friendship) {
        jdbcClient.sql("""
                        INSERT INTO friendship (friendship_id, requester_id, addressee_id, status, version, created_at, updated_at)
                        VALUES (:friendshipId, :requesterId, :addresseeId, CAST(:status AS friend_status), :version, :createdAt, :updatedAt)
                        ON CONFLICT (requester_id, addressee_id) DO UPDATE SET
                            status     = EXCLUDED.status,
                            version    = friendship.version + 1,
                            updated_at = EXCLUDED.updated_at
                        """)
                .param("friendshipId", UUID.fromString(friendship.getFriendshipId()))
                .param("requesterId",  friendship.getRequesterId())
                .param("addresseeId",  friendship.getAddresseeId())
                .param("status",       friendship.getStatus())
                .param("version",      friendship.getVersion())
                .param("createdAt",    Timestamp.from(friendship.getCreatedAt()))
                .param("updatedAt",    Timestamp.from(friendship.getUpdatedAt()))
                .update();
    }

    @Override
    public Optional<Friendship> findFriendshipById(String friendshipId) {
        return jdbcClient.sql("""
                        SELECT friendship_id::text, requester_id::text, addressee_id::text,
                               status::text, version, created_at, updated_at
                        FROM friendship
                        WHERE friendship_id = :id
                        """)
                .param("id", UUID.fromString(friendshipId))
                .query(FRIENDSHIP_MAPPER)
                .optional();
    }

    @Override
    public Optional<Friendship> findPendingFriendship(UUID requesterId, UUID addresseeId) {
        return jdbcClient.sql("""
                        SELECT friendship_id::text, requester_id::text, addressee_id::text,
                               status::text, version, created_at, updated_at
                        FROM friendship
                        WHERE status = CAST('PENDING' AS friend_status)
                          AND ((requester_id = :requesterId AND addressee_id = :addresseeId)
                            OR (requester_id = :addresseeId AND addressee_id = :requesterId))
                        """)
                .param("requesterId", requesterId)
                .param("addresseeId", addresseeId)
                .query(FRIENDSHIP_MAPPER)
                .optional();
    }

    @Override
    public List<Friendship> findIncomingPendingRequests(UUID addresseeId) {
        return jdbcClient.sql("""
                        SELECT friendship_id::text, requester_id::text, addressee_id::text,
                               status::text, version, created_at, updated_at
                        FROM friendship
                        WHERE addressee_id = :addresseeId AND status = CAST('PENDING' AS friend_status)
                        ORDER BY created_at DESC
                        """)
                .param("addresseeId", addresseeId)
                .query(FRIENDSHIP_MAPPER)
                .list();
    }

    @Override
    public List<Friendship> findOutgoingPendingRequests(UUID requesterId) {
        return jdbcClient.sql("""
                        SELECT friendship_id::text, requester_id::text, addressee_id::text,
                               status::text, version, created_at, updated_at
                        FROM friendship
                        WHERE requester_id = :requesterId AND status = CAST('PENDING' AS friend_status)
                        ORDER BY created_at DESC
                        """)
                .param("requesterId", requesterId)
                .query(FRIENDSHIP_MAPPER)
                .list();
    }

    @Override
    public List<UUID> getAcceptedFriendIds(UUID userId) {
        return jdbcClient.sql("""
                        SELECT CASE WHEN requester_id = :userId THEN addressee_id ELSE requester_id END AS friend_id
                        FROM friendship
                        WHERE (requester_id = :userId OR addressee_id = :userId)
                          AND status = CAST('ACCEPTED' AS friend_status)
                        """)
                .param("userId", userId)
                .query(UUID.class)
                .list();
    }

    @Override
    public void removeFriendship(UUID userId1, UUID userId2) {
        jdbcClient.sql("""
                        UPDATE friendship
                        SET status     = CAST('DECLINED' AS friend_status),
                            updated_at = now(),
                            version    = version + 1
                        WHERE ((requester_id = :a AND addressee_id = :b)
                            OR (requester_id = :b AND addressee_id = :a))
                          AND status = CAST('ACCEPTED' AS friend_status)
                        """)
                .param("a", userId1)
                .param("b", userId2)
                .update();
    }
}
