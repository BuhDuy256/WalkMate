package com.walkmate.infrastructure.repository.walkintent;

import com.walkmate.domain.walkintent.IntentStatus;
import com.walkmate.domain.walkintent.MatchingConstraints;
import com.walkmate.domain.walkintent.WalkIntent;
import com.walkmate.domain.walkintent.WalkIntentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class WalkIntentJdbcRepository implements WalkIntentRepository {

    private final JdbcClient jdbcClient;

    @Override
    public WalkIntent save(WalkIntent intent) {
        final String sql = """
                INSERT INTO walk_intent (
                    intent_id, hotspot_id, user_id,
                    time_window_start, time_window_end,
                    matching_constraints,
                    status, created_at, expires_at, version,
                    is_private, invited_friend_id, description,
                    excluded_user_ids
                )
                VALUES (
                    :intentId,
                    :hotspotId,
                    :userId,
                    :timeWindowStart,
                    :timeWindowEnd,
                    CAST(:matchingConstraints AS jsonb),
                    CAST(:status AS intent_status),
                    :createdAt,
                    :expiresAt,
                    :version,
                    :isPrivate,
                    :invitedFriendId,
                    :description,
                    CAST(:excludedUserIds AS uuid[])
                )
                ON CONFLICT (intent_id) DO UPDATE SET
                    status               = CAST(EXCLUDED.status AS intent_status),
                    time_window_start    = EXCLUDED.time_window_start,
                    time_window_end      = EXCLUDED.time_window_end,
                    matching_constraints = EXCLUDED.matching_constraints,
                    expires_at           = EXCLUDED.expires_at,
                    version              = EXCLUDED.version,
                    is_private           = EXCLUDED.is_private,
                    invited_friend_id    = EXCLUDED.invited_friend_id,
                    description          = EXCLUDED.description,
                    excluded_user_ids    = EXCLUDED.excluded_user_ids
                """;

        jdbcClient.sql(sql)
                .param("intentId",           UUID.fromString(intent.getId()))
                .param("hotspotId",          UUID.fromString(intent.getHotspotId()))
                .param("userId",             UUID.fromString(intent.getUserId()))
                .param("timeWindowStart",    Timestamp.from(intent.getTimeWindowStart()))
                .param("timeWindowEnd",      Timestamp.from(intent.getTimeWindowEnd()))
                .param("matchingConstraints", toJsonb(intent.getMatchingConstraints()))
                .param("status",             intent.getStatus().name())
                .param("createdAt",          Timestamp.from(intent.getCreatedAt()))
                .param("expiresAt",          Timestamp.from(intent.getExpiresAt()))
                .param("version",            intent.getVersion())
                .param("isPrivate",          intent.isPrivate())
                .param("invitedFriendId",    intent.getInvitedFriendId() != null
                                                 ? UUID.fromString(intent.getInvitedFriendId()) : null)
                .param("description",        intent.getDescription())
                .param("excludedUserIds",    toUuidArrayLiteral(intent.getExcludedUserIds()))
                .update();

        return intent;
    }

    @Override
    public Optional<WalkIntent> findById(String id) {
        final String sql = """
                SELECT
                    intent_id::text,
                    hotspot_id::text,
                    user_id::text,
                    time_window_start,
                    time_window_end,
                    (matching_constraints->>'age_min')::int       AS age_min,
                    (matching_constraints->>'age_max')::int       AS age_max,
                    matching_constraints->>'preferred_gender'     AS preferred_gender,
                    status,
                    created_at,
                    expires_at,
                    version,
                    is_private,
                    invited_friend_id::text,
                    description,
                    excluded_user_ids
                FROM walk_intent
                WHERE intent_id = :intentId
                """;

        return jdbcClient.sql(sql)
                .param("intentId", UUID.fromString(id))
                .query((rs, rowNum) -> mapRow(rs))
                .optional();
    }

    @Override
    public Optional<WalkIntent> findByIdForUpdate(String id) {
        final String sql = """
                SELECT
                    intent_id::text,
                    hotspot_id::text,
                    user_id::text,
                    time_window_start,
                    time_window_end,
                    (matching_constraints->>'age_min')::int       AS age_min,
                    (matching_constraints->>'age_max')::int       AS age_max,
                    matching_constraints->>'preferred_gender'     AS preferred_gender,
                    status,
                    created_at,
                    expires_at,
                    version,
                    is_private,
                    invited_friend_id::text,
                    description,
                    excluded_user_ids
                FROM walk_intent
                WHERE intent_id = :intentId
                FOR UPDATE
                """;

        return jdbcClient.sql(sql)
                .param("intentId", UUID.fromString(id))
                .query((rs, rowNum) -> mapRow(rs))
                .optional();
    }

    @Override
    public void delete(String id) {
        jdbcClient.sql("DELETE FROM walk_intent WHERE intent_id = :intentId")
                .param("intentId", UUID.fromString(id))
                .update();
    }

    @Override
    public List<WalkIntent> findOpenByUserId(String userId) {
        final String sql = """
                SELECT
                    wi.intent_id::text,
                    wi.hotspot_id::text,
                    h.name                                        AS hotspot_name,
                    wi.user_id::text,
                    wi.time_window_start,
                    wi.time_window_end,
                    (wi.matching_constraints->>'age_min')::int    AS age_min,
                    (wi.matching_constraints->>'age_max')::int    AS age_max,
                    wi.matching_constraints->>'preferred_gender'  AS preferred_gender,
                    wi.status,
                    wi.created_at,
                    wi.expires_at,
                    wi.version,
                    wi.is_private,
                    wi.invited_friend_id::text,
                    wi.description,
                    wi.excluded_user_ids
                FROM walk_intent wi
                JOIN hotspot h ON h.id = wi.hotspot_id
                WHERE wi.user_id = :userId
                  AND wi.status IN ('OPEN', 'MATCHING')
                  AND wi.is_private = false
                ORDER BY wi.created_at DESC
                """;

        return jdbcClient.sql(sql)
                .param("userId", UUID.fromString(userId))
                .query((rs, rowNum) -> mapRow(rs))
                .list();
    }

    @Override
    public boolean hasOverlappingActiveIntent(String userId, Instant start, Instant end) {
        final String sql = """
                SELECT COUNT(*) FROM walk_intent
                WHERE user_id = :userId
                  AND status IN ('OPEN', 'MATCHING')
                  AND time_window_start < :end
                  AND time_window_end   > :start
                """;

        Integer count = jdbcClient.sql(sql)
                .param("userId", UUID.fromString(userId))
                .param("start",  Timestamp.from(start))
                .param("end",    Timestamp.from(end))
                .query(Integer.class)
                .single();

        return count != null && count > 0;
    }

    /**
     * Stage 1 of matching: DB-level hard filter.
     *
     * Time-overlap condition (Research.md §4):
     *   candidate.time_window_start < (timeWindowEnd   - minDuration)
     *   candidate.time_window_end   > (timeWindowStart + minDuration)
     *
     * This guarantees the shared window is ≥ minDuration before any
     * candidate reaches the application layer for scoring.
     *
     * Age-preference overlap (mutual comfort range intersection):
     *   candidate.age_min <= ageMax
     *   candidate.age_max >= ageMin
     *
     * Exclude list (X-3, GAP-11):
     *   :callerId != ALL(wi.excluded_user_ids)
     *   Filters out intents whose owner has already excluded the caller.
     */
    @Override
    public List<WalkIntent> findOpenCandidates(
            String hotspotId,
            Instant timeWindowStart,
            Instant timeWindowEnd,
            int ageMin,
            int ageMax,
            String excludeUserId,
            Duration minDuration,
            String preferredGender) {

        Instant boundaryEnd   = timeWindowEnd.minus(minDuration);
        Instant boundaryStart = timeWindowStart.plus(minDuration);

        if (preferredGender == null || preferredGender.isBlank()) preferredGender = "ANY";

        // Gender filter: if preferredGender != "ANY", only include candidates whose
        // user_profile.gender matches the requested value.
        final String sql = """
                SELECT
                    wi.intent_id::text,
                    wi.hotspot_id::text,
                    wi.user_id::text,
                    wi.time_window_start,
                    wi.time_window_end,
                    (wi.matching_constraints->>'age_min')::int       AS age_min,
                    (wi.matching_constraints->>'age_max')::int       AS age_max,
                    wi.matching_constraints->>'preferred_gender'     AS preferred_gender,
                    wi.status,
                    wi.created_at,
                    wi.expires_at,
                    wi.version,
                    wi.is_private,
                    wi.invited_friend_id::text,
                    wi.description,
                    wi.excluded_user_ids
                FROM walk_intent wi
                LEFT JOIN user_profile up ON up.user_id = wi.user_id
                WHERE wi.hotspot_id          = :hotspotId
                  AND wi.status              = 'OPEN'
                  AND wi.user_id            != :excludeUserId
                  AND (wi.is_private = false OR wi.invited_friend_id = :callerId)
                  AND :callerId != ALL(wi.excluded_user_ids)
                  AND (wi.matching_constraints->>'age_min')::int <= :ageMax
                  AND (wi.matching_constraints->>'age_max')::int >= :ageMin
                  AND wi.time_window_start   < :boundaryEnd
                  AND wi.time_window_end     > :boundaryStart
                  AND (:preferredGender = 'ANY' OR up.gender::text = :preferredGender)
                ORDER BY wi.created_at ASC
                """;

        return jdbcClient.sql(sql)
                .param("hotspotId",       UUID.fromString(hotspotId))
                .param("excludeUserId",   UUID.fromString(excludeUserId))
                .param("callerId",        UUID.fromString(excludeUserId))
                .param("ageMin",          ageMin)
                .param("ageMax",          ageMax)
                .param("boundaryEnd",     Timestamp.from(boundaryEnd))
                .param("boundaryStart",   Timestamp.from(boundaryStart))
                .param("preferredGender", preferredGender)
                .query((rs, rowNum) -> mapRow(rs))
                .list();
    }

    @Override
    public List<WalkIntent> findIntentsExpiringSoon(Instant now, Duration buffer) {
        // Intents whose start time is within the buffer window AND whose window
        // has not yet ended. These are about to become stale (GAP-14 / Note #2).
        Instant cutoff = now.plus(buffer);
        final String sql = """
                SELECT
                    intent_id::text,
                    hotspot_id::text,
                    user_id::text,
                    time_window_start,
                    time_window_end,
                    (matching_constraints->>'age_min')::int       AS age_min,
                    (matching_constraints->>'age_max')::int       AS age_max,
                    matching_constraints->>'preferred_gender'     AS preferred_gender,
                    status,
                    created_at,
                    expires_at,
                    version,
                    is_private,
                    invited_friend_id::text,
                    description,
                    excluded_user_ids
                FROM walk_intent
                WHERE time_window_start <= :cutoff
                  AND time_window_end   > :now
                  AND status IN ('OPEN', 'MATCHING')
                ORDER BY time_window_start ASC
                """;

        return jdbcClient.sql(sql)
                .param("cutoff", Timestamp.from(cutoff))
                .param("now",    Timestamp.from(now))
                .query((rs, rowNum) -> mapRow(rs))
                .list();
    }

    @Override
    public List<WalkIntent> findOverdueOpenIntents(Instant now) {
        // Intents whose entire time window has already passed (GAP-13).
        final String sql = """
                SELECT
                    intent_id::text,
                    hotspot_id::text,
                    user_id::text,
                    time_window_start,
                    time_window_end,
                    (matching_constraints->>'age_min')::int       AS age_min,
                    (matching_constraints->>'age_max')::int       AS age_max,
                    matching_constraints->>'preferred_gender'     AS preferred_gender,
                    status,
                    created_at,
                    expires_at,
                    version,
                    is_private,
                    invited_friend_id::text,
                    description,
                    excluded_user_ids
                FROM walk_intent
                WHERE time_window_end <= :now
                  AND status IN ('OPEN', 'MATCHING')
                ORDER BY time_window_end ASC
                """;

        return jdbcClient.sql(sql)
                .param("now", Timestamp.from(now))
                .query((rs, rowNum) -> mapRow(rs))
                .list();
    }

    // ── Private helpers ───────────────────────────────────────────────────

    private WalkIntent mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        String pg = rs.getString("preferred_gender");
        if (pg == null || pg.isBlank()) pg = "ANY";
        String hotspotName;
        try { hotspotName = rs.getString("hotspot_name"); } catch (Exception e) { hotspotName = null; }
        return new WalkIntent(
                rs.getString("intent_id"),
                rs.getString("hotspot_id"),
                hotspotName,
                rs.getString("user_id"),
                rs.getTimestamp("time_window_start").toInstant(),
                rs.getTimestamp("time_window_end").toInstant(),
                new MatchingConstraints(rs.getInt("age_min"), rs.getInt("age_max"), pg),
                IntentStatus.valueOf(rs.getString("status")),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("expires_at").toInstant(),
                rs.getLong("version"),
                rs.getBoolean("is_private"),
                rs.getString("invited_friend_id"),
                rs.getString("description"),
                readUuidArray(rs, "excluded_user_ids")
        );
    }

    /**
     * Reads a PostgreSQL uuid[] column as a List<UUID>.
     */
    private static List<UUID> readUuidArray(java.sql.ResultSet rs, String column)
            throws java.sql.SQLException {
        Array sqlArray = rs.getArray(column);
        List<UUID> result = new ArrayList<>();
        if (sqlArray != null) {
            Object[] arr = (Object[]) sqlArray.getArray();
            for (Object o : arr) {
                if (o != null) result.add(UUID.fromString(o.toString()));
            }
        }
        return result;
    }

    /**
     * Converts a List<UUID> to a PostgreSQL array literal string, e.g. {@code {uuid1,uuid2}}.
     * Used with {@code CAST(:param AS uuid[])} in SQL.
     */
    private static String toUuidArrayLiteral(List<UUID> uuids) {
        if (uuids == null || uuids.isEmpty()) return "{}";
        return "{" + uuids.stream().map(UUID::toString).collect(Collectors.joining(",")) + "}";
    }

    /**
     * Serialises MatchingConstraints to a JSONB-compatible string.
     * Uses String.format to avoid a Jackson dependency in the repository.
     */
    private String toJsonb(MatchingConstraints constraints) {
        return String.format("{\"age_min\":%d,\"age_max\":%d,\"preferred_gender\":\"%s\"}",
                constraints.ageMin(), constraints.ageMax(), constraints.preferredGender());
    }
}
