package com.walkmate.gps.support;

import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Raw-SQL test fixtures and final-state assertions for the v2 GPS suite.
 * Seeding and assertions go directly through
 * {@link JdbcClient} (the source of truth), never through the services
 * under test — the services' contract is verified end to end.
 */
public final class GpsFixtures {

    private static final String INTENT_SQL = """
            INSERT INTO walk_intent (
                intent_id, hotspot_id, user_id,
                time_window_start, time_window_end,
                matching_constraints, status, created_at, expires_at, version,
                is_private, excluded_user_ids
            )
            VALUES (
                :intentId, :hotspotId, :userId,
                :start, :end,
                '{"age_min":18,"age_max":99,"preferred_gender":"ANY"}'::jsonb,
                'OPEN', now(), :end, 0,
                false, '{}'
            )
            """;

    private GpsFixtures() {
    }

    // ── Seed helpers ──────────────────────────────────────────────────────────

    public static void seedUser(JdbcClient jdbc, String userId, String email) {
        jdbc.sql("""
                        INSERT INTO user_account (user_id, email, trust_score)
                        VALUES (:userId, :email, 500)
                        ON CONFLICT (user_id) DO NOTHING
                        """)
                .param("userId", UUID.fromString(userId))
                .param("email", email)
                .update();

        jdbc.sql("""
                        INSERT INTO user_profile (user_id, full_name, gender)
                        VALUES (:userId, :fullName, 'FEMALE')
                        ON CONFLICT (user_id) DO NOTHING
                        """)
                .param("userId", UUID.fromString(userId))
                .param("fullName", "User " + userId.substring(0, 8))
                .update();
    }

    /** Seeds the minimal proposal/intent/hotspot chain for a session FK. */
    public static void seedSessionChain(JdbcClient jdbc, String sessionId, String userIdA,
                                        String userIdB, String status,
                                        String userAStatus, String userBStatus) {
        String intentAId  = UUID.randomUUID().toString();
        String intentBId  = UUID.randomUUID().toString();
        String proposalId = UUID.randomUUID().toString();
        String hotspotId  = UUID.randomUUID().toString();

        jdbc.sql("""
                        INSERT INTO hotspot (id, name, lat, lng)
                        VALUES (:id, 'Test Hotspot', 10.776, 106.700)
                        ON CONFLICT (id) DO NOTHING
                        """)
                .param("id", UUID.fromString(hotspotId))
                .update();

        Instant now = Instant.now();
        jdbc.sql(INTENT_SQL)
                .param("intentId", UUID.fromString(intentAId))
                .param("hotspotId", UUID.fromString(hotspotId))
                .param("userId", UUID.fromString(userIdA))
                .param("start", java.sql.Timestamp.from(now.minusSeconds(3600)))
                .param("end", java.sql.Timestamp.from(now.plusSeconds(3600)))
                .update();
        jdbc.sql(INTENT_SQL)
                .param("intentId", UUID.fromString(intentBId))
                .param("hotspotId", UUID.fromString(hotspotId))
                .param("userId", UUID.fromString(userIdB))
                .param("start", java.sql.Timestamp.from(now.minusSeconds(3600)))
                .param("end", java.sql.Timestamp.from(now.plusSeconds(3600)))
                .update();

        jdbc.sql("""
                        INSERT INTO match_proposal (
                            proposal_id, intent_id_a, intent_id_b,
                            proposed_start_time, proposed_end_time,
                            hotspot_id, accepted_by_a, accepted_by_b,
                            status, created_at, expires_at, version
                        )
                        VALUES (
                            :proposalId, :intentAId, :intentBId,
                            :start, :end,
                            :hotspotId, true, true,
                            'CONFIRMED', now(), :end, 0
                        )
                        """)
                .param("proposalId", UUID.fromString(proposalId))
                .param("intentAId", UUID.fromString(intentAId))
                .param("intentBId", UUID.fromString(intentBId))
                .param("hotspotId", UUID.fromString(hotspotId))
                .param("start", java.sql.Timestamp.from(now.minusSeconds(1800)))
                .param("end", java.sql.Timestamp.from(now.plusSeconds(1800)))
                .update();

        jdbc.sql("""
                        INSERT INTO walk_session (
                            session_id, proposal_id, user_id_a, user_id_b, hotspot_id,
                            scheduled_start, scheduled_end, status,
                            created_at, started_at, version,
                            user_a_status, user_b_status
                        )
                        VALUES (
                            :sessionId, :proposalId, :userIdA, :userIdB, :hotspotId,
                            :start, :end, CAST(:status AS walk_session_status),
                            now(), now(), 0,
                            CAST(:userAStatus AS walk_session_status),
                            CAST(:userBStatus AS walk_session_status)
                        )
                        """)
                .param("sessionId", UUID.fromString(sessionId))
                .param("proposalId", UUID.fromString(proposalId))
                .param("userIdA", UUID.fromString(userIdA))
                .param("userIdB", UUID.fromString(userIdB))
                .param("hotspotId", UUID.fromString(hotspotId))
                .param("start", java.sql.Timestamp.from(now.minusSeconds(1800)))
                .param("end", java.sql.Timestamp.from(now.plusSeconds(1800)))
                .param("status", status)
                .param("userAStatus", userAStatus)
                .param("userBStatus", userBStatus)
                .update();
    }

    /** Puts the caller's side of an ACTIVE session into COMPLETED (post-Finish state). */
    public static void markUserCompleted(JdbcClient jdbc, String sessionId, String userId) {
        jdbc.sql("""
                        UPDATE walk_session
                           SET user_a_status = CASE WHEN :userId = user_id_a
                                                    THEN 'COMPLETED'::walk_session_status
                                                    ELSE user_a_status END,
                               user_b_status = CASE WHEN :userId = user_id_b
                                                    THEN 'COMPLETED'::walk_session_status
                                                    ELSE user_b_status END
                         WHERE session_id = :sessionId
                        """)
                .param("userId", UUID.fromString(userId))
                .param("sessionId", UUID.fromString(sessionId))
                .update();
    }

    /** Backdates the upload deadline so the late-upload window has closed. */
    public static void expireStream(JdbcClient jdbc, String streamId) {
        jdbc.sql("""
                        UPDATE tracking_streams
                           SET upload_deadline_at = now() - interval '1 second',
                               updated_at = now()
                         WHERE stream_id = :streamId
                        """)
                .param("streamId", UUID.fromString(streamId))
                .update();
    }

    /** Force-sets the stream's uploaded final sequence (simulates a missed seal). */    public static void setExpectedFinalSequence(JdbcClient jdbc, String streamId, long value) {
        jdbc.sql("""
                        UPDATE tracking_streams
                           SET expected_final_sequence = :expected
                         WHERE stream_id = :streamId
                        """)
                .param("expected", value)
                .param("streamId", UUID.fromString(streamId))
                .update();
    }

    // ── State assertions (direct SQL on the source of truth) ──────────────────

    public static String streamSyncState(JdbcClient jdbc, String streamId) {
        return jdbc.sql("""
                        SELECT sync_state FROM tracking_streams WHERE stream_id = :streamId
                        """)
                .param("streamId", UUID.fromString(streamId))
                .query(String.class)
                .single();
    }

    public static long countV2Chunks(JdbcClient jdbc, String sessionId, String userId) {
        Long count = jdbc.sql("""
                        SELECT COUNT(*)
                          FROM session_point_chunks
                         WHERE session_id = :sessionId
                           AND user_id    = :userId
                           AND stream_id IS NOT NULL
                        """)
                .param("sessionId", UUID.fromString(sessionId))
                .param("userId", UUID.fromString(userId))
                .query(Long.class)
                .single();
        return count != null ? count : 0L;
    }

    /** Stored canonical hash for a batch id, or {@code null} when nothing committed. */
    public static String chunkHashOfBatch(JdbcClient jdbc, String batchId) {
        return jdbc.sql("""
                        SELECT payload_hash FROM session_point_chunks
                         WHERE sync_request_id = :batchId
                        """)
                .param("batchId", UUID.fromString(batchId))
                .query(String.class)
                .optional()
                .orElse(null);
    }

    public static boolean hasChunkWithRange(JdbcClient jdbc, String streamId,
                                            long seqStart, long seqEnd) {
        Boolean found = jdbc.sql("""
                        SELECT EXISTS (
                            SELECT 1 FROM session_point_chunks
                             WHERE stream_id = :streamId
                               AND seq_start = :seqStart AND seq_end = :seqEnd
                        )
                        """)
                .param("streamId", UUID.fromString(streamId))
                .param("seqStart", seqStart)
                .param("seqEnd", seqEnd)
                .query(Boolean.class)
                .single();
        return Boolean.TRUE.equals(found);
    }

    public static int nextArrivalIndex(JdbcClient jdbc, String streamId) {
        Integer idx = jdbc.sql("""
                        SELECT next_arrival_index FROM tracking_streams WHERE stream_id = :streamId
                        """)
                .param("streamId", UUID.fromString(streamId))
                .query(Integer.class)
                .single();
        return idx != null ? idx : -1;
    }

    // ── Extra helpers for the edge scenarios ──────────────────────────────────

    /**
     * Inserts a legacy (v1) chunk — {@code stream_id IS NULL} — occupying the
     * given {@code chunk_index}. Used to prove that a non-duplicate integrity
     * failure aborts a v2 write instead of being misreported as a replay ACK.
     */
    public static void seedLegacyChunk(JdbcClient jdbc, String sessionId, String userId,
                                       int chunkIndex, int pointCount) {
        jdbc.sql("""
                        INSERT INTO session_point_chunks
                            (session_id, user_id, chunk_index, polyline, point_count)
                        VALUES
                            (:sessionId, :userId, :chunkIndex, 'legacy', :pointCount)
                        """)
                .param("sessionId", UUID.fromString(sessionId))
                .param("userId", UUID.fromString(userId))
                .param("chunkIndex", chunkIndex)
                .param("pointCount", pointCount)
                .update();
    }

    /** Committed v2 ranges for a stream, ordered by route sequence (not arrival). */
    public static List<long[]> committedRanges(JdbcClient jdbc, String streamId) {
        return jdbc.sql("""
                        SELECT seq_start, seq_end
                          FROM session_point_chunks
                         WHERE stream_id = :streamId
                         ORDER BY seq_start ASC
                        """)
                .param("streamId", UUID.fromString(streamId))
                .query((rs, rowNum) -> new long[]{rs.getLong("seq_start"), rs.getLong("seq_end")})
                .list();
    }

    /** Arrival-ordered chunk indexes, proving each batch stored exactly once. */
    public static List<Integer> arrivalOrder(JdbcClient jdbc, String streamId) {
        return jdbc.sql("""
                        SELECT chunk_index
                          FROM session_point_chunks
                         WHERE stream_id = :streamId
                         ORDER BY chunk_index ASC
                        """)
                .param("streamId", UUID.fromString(streamId))
                .query(Integer.class)
                .list();
    }

    public static long countStreams(JdbcClient jdbc, String sessionId, String userId) {
        Long count = jdbc.sql("""
                        SELECT COUNT(*) FROM tracking_streams
                         WHERE session_id = :sessionId AND user_id = :userId
                        """)
                .param("sessionId", UUID.fromString(sessionId))
                .param("userId", UUID.fromString(userId))
                .query(Long.class)
                .single();
        return count != null ? count : 0L;
    }

    /** Moves the whole session to a terminal business status (route read path). */
    public static void setSessionStatus(JdbcClient jdbc, String sessionId, String status) {
        jdbc.sql("""
                        UPDATE walk_session
                           SET status        = CAST(:status AS walk_session_status),
                               user_a_status = CAST(:status AS walk_session_status),
                               user_b_status = CAST(:status AS walk_session_status)
                         WHERE session_id = :sessionId
                        """)
                .param("status", status)
                .param("sessionId", UUID.fromString(sessionId))
                .update();
    }

    /** Polylines for one participant in ARRIVAL order ({@code chunk_index}). */
    public static List<String> polylinesByArrival(JdbcClient jdbc, String sessionId, String userId) {
        return jdbc.sql("""
                        SELECT polyline FROM session_point_chunks
                         WHERE session_id = :sessionId AND user_id = :userId
                         ORDER BY chunk_index ASC
                        """)
                .param("sessionId", UUID.fromString(sessionId))
                .param("userId", UUID.fromString(userId))
                .query(String.class)
                .list();
    }

    /** Polylines for one participant in ROUTE order ({@code seq_start}). */
    public static List<String> polylinesBySequence(JdbcClient jdbc, String sessionId, String userId) {
        return jdbc.sql("""
                        SELECT polyline FROM session_point_chunks
                         WHERE session_id = :sessionId AND user_id = :userId
                         ORDER BY seq_start ASC
                        """)
                .param("sessionId", UUID.fromString(sessionId))
                .param("userId", UUID.fromString(userId))
                .query(String.class)
                .list();
    }

    /** Inserts a legacy-era chunk row (no stream, no sequence) at a given arrival index. */
    public static void seedLegacyChunkAt(JdbcClient jdbc, String sessionId, String userId,
                                         int chunkIndex, String polyline, int pointCount) {
        jdbc.sql("""
                        INSERT INTO session_point_chunks
                            (session_id, user_id, chunk_index, polyline, point_count)
                        VALUES
                            (:sessionId, :userId, :chunkIndex, :polyline, :pointCount)
                        """)
                .param("sessionId", UUID.fromString(sessionId))
                .param("userId", UUID.fromString(userId))
                .param("chunkIndex", chunkIndex)
                .param("polyline", polyline)
                .param("pointCount", pointCount)
                .update();
    }
}
