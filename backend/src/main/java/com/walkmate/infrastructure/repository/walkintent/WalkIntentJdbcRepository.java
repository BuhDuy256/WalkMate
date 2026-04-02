package com.walkmate.infrastructure.repository.walkintent;

import com.walkmate.domain.walkintent.IntentStatus;
import com.walkmate.domain.walkintent.MatchingConstraints;
import com.walkmate.domain.walkintent.WalkIntent;
import com.walkmate.domain.walkintent.WalkIntentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
                    status, created_at, expires_at, version
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
                    :version
                )
                ON CONFLICT (intent_id) DO UPDATE SET
                    status              = CAST(EXCLUDED.status AS intent_status),
                    time_window_start   = EXCLUDED.time_window_start,
                    time_window_end     = EXCLUDED.time_window_end,
                    matching_constraints = EXCLUDED.matching_constraints,
                    expires_at          = EXCLUDED.expires_at,
                    version             = EXCLUDED.version
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
                    (matching_constraints->>'age_min')::int  AS age_min,
                    (matching_constraints->>'age_max')::int  AS age_max,
                    status,
                    created_at,
                    expires_at,
                    version
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
                    (matching_constraints->>'age_min')::int AS age_min,
                    (matching_constraints->>'age_max')::int AS age_max,
                    status,
                    created_at,
                    expires_at,
                    version
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
                    intent_id::text,
                    hotspot_id::text,
                    user_id::text,
                    time_window_start,
                    time_window_end,
                    (matching_constraints->>'age_min')::int AS age_min,
                    (matching_constraints->>'age_max')::int AS age_max,
                    status,
                    created_at,
                    expires_at,
                    version
                FROM walk_intent
                WHERE user_id = :userId
                  AND status = 'OPEN'
                ORDER BY created_at DESC
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
                  AND status IN ('OPEN', 'CONSUMED')
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
     */
    @Override
    public List<WalkIntent> findOpenCandidates(
            String hotspotId,
            Instant timeWindowStart,
            Instant timeWindowEnd,
            int ageMin,
            int ageMax,
            String excludeUserId,
            Duration minDuration) {

        Instant boundaryEnd   = timeWindowEnd.minus(minDuration);
        Instant boundaryStart = timeWindowStart.plus(minDuration);

        final String sql = """
                SELECT
                    intent_id::text,
                    hotspot_id::text,
                    user_id::text,
                    time_window_start,
                    time_window_end,
                    (matching_constraints->>'age_min')::int  AS age_min,
                    (matching_constraints->>'age_max')::int  AS age_max,
                    status,
                    created_at,
                    expires_at,
                    version
                FROM walk_intent
                WHERE hotspot_id          = :hotspotId
                  AND status              = 'OPEN'
                  AND user_id            != :excludeUserId
                  AND (matching_constraints->>'age_min')::int <= :ageMax
                  AND (matching_constraints->>'age_max')::int >= :ageMin
                  AND time_window_start   < :boundaryEnd
                  AND time_window_end     > :boundaryStart
                ORDER BY created_at ASC
                """;

        return jdbcClient.sql(sql)
                .param("hotspotId",     UUID.fromString(hotspotId))
                .param("excludeUserId", UUID.fromString(excludeUserId))
                .param("ageMin",        ageMin)
                .param("ageMax",        ageMax)
                .param("boundaryEnd",   Timestamp.from(boundaryEnd))
                .param("boundaryStart", Timestamp.from(boundaryStart))
                .query((rs, rowNum) -> mapRow(rs))
                .list();
    }

    // ── Private helpers ───────────────────────────────────────────────────

    private WalkIntent mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new WalkIntent(
                rs.getString("intent_id"),
                rs.getString("hotspot_id"),
                rs.getString("user_id"),
                rs.getTimestamp("time_window_start").toInstant(),
                rs.getTimestamp("time_window_end").toInstant(),
                new MatchingConstraints(rs.getInt("age_min"), rs.getInt("age_max")),
                IntentStatus.valueOf(rs.getString("status")),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("expires_at").toInstant(),
                rs.getLong("version")
        );
    }

    /**
     * Serialises MatchingConstraints to a JSONB-compatible string.
     * Uses String.format to avoid a Jackson dependency in the repository.
     */
    private String toJsonb(MatchingConstraints constraints) {
        return String.format("{\"age_min\":%d,\"age_max\":%d}",
                constraints.ageMin(), constraints.ageMax());
    }
}
