package com.walkmate.matching.support;

import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.List;
import java.util.UUID;

/**
 * Raw-SQL test fixtures and final-state assertions. Seeding/assertions go directly through
 * {@link JdbcClient} (the source of truth), never through the services under test.
 */
public final class Fixtures {

    private Fixtures() {
    }

    public static void seedUser(JdbcClient jdbc, String userId, String email, String gender) {
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
                        VALUES (:userId, :fullName, CAST(:gender AS gender))
                        ON CONFLICT (user_id) DO NOTHING
                        """)
                .param("userId", UUID.fromString(userId))
                .param("fullName", "User " + userId.substring(0, 8))
                .param("gender", gender)
                .update();
    }

    public static void seedHotspot(JdbcClient jdbc, String hotspotId, String name) {
        jdbc.sql("""
                        INSERT INTO hotspot (id, name, lat, lng)
                        VALUES (:id, :name, 10.0, 106.0)
                        ON CONFLICT (id) DO NOTHING
                        """)
                .param("id", UUID.fromString(hotspotId))
                .param("name", name)
                .update();
    }

    public static void seedFriendship(JdbcClient jdbc, String requesterId, String addresseeId) {
        jdbc.sql("""
                        INSERT INTO friendship (friendship_id, requester_id, addressee_id, status)
                        VALUES (:friendshipId, :requesterId, :addresseeId, 'ACCEPTED')
                        ON CONFLICT (requester_id, addressee_id) DO NOTHING
                        """)
                .param("friendshipId", UUID.randomUUID())
                .param("requesterId", UUID.fromString(requesterId))
                .param("addresseeId", UUID.fromString(addresseeId))
                .update();
    }

    /** Inserts a raw OPEN intent directly, bypassing the services (deterministic setup). */
    public static void seedOpenIntent(JdbcClient jdbc, String intentId, String hotspotId,
                                      String userId, java.time.Instant start, java.time.Instant end) {
        jdbc.sql("""
                        INSERT INTO walk_intent (
                            intent_id, hotspot_id, user_id,
                            time_window_start, time_window_end,
                            matching_constraints, status, created_at, expires_at, version,
                            is_private, invited_friend_id, description, excluded_user_ids
                        )
                        VALUES (
                            :intentId, :hotspotId, :userId,
                            :start, :end,
                            '{"age_min":18,"age_max":99,"preferred_gender":"ANY"}'::jsonb,
                            'OPEN', now(), :end, 0,
                            false, NULL, NULL, '{}'
                        )
                        """)
                .param("intentId", UUID.fromString(intentId))
                .param("hotspotId", UUID.fromString(hotspotId))
                .param("userId", UUID.fromString(userId))
                .param("start", java.sql.Timestamp.from(start))
                .param("end", java.sql.Timestamp.from(end))
                .update();
    }

    /** Inserts a PENDING session (to test the session-overlap guard). */
    public static void seedPendingSession(JdbcClient jdbc, String sessionId, String proposalId,
                                          String userIdA, String userIdB, String hotspotId,
                                          java.time.Instant start, java.time.Instant end) {
        jdbc.sql("""
                        INSERT INTO walk_session (
                            session_id, proposal_id, user_id_a, user_id_b, hotspot_id,
                            scheduled_start, scheduled_end, status, created_at, version,
                            user_a_status, user_b_status,
                            user_a_distance_km, user_a_duration_seconds,
                            user_b_distance_km, user_b_duration_seconds
                        )
                        VALUES (
                            :sessionId, :proposalId, :userIdA, :userIdB, :hotspotId,
                            :start, :end, 'PENDING', now(), 0,
                            'PENDING', 'PENDING', 0, 0, 0, 0
                        )
                        """)
                .param("sessionId", UUID.fromString(sessionId))
                .param("proposalId", UUID.fromString(proposalId))
                .param("userIdA", UUID.fromString(userIdA))
                .param("userIdB", UUID.fromString(userIdB))
                .param("hotspotId", UUID.fromString(hotspotId))
                .param("start", java.sql.Timestamp.from(start))
                .param("end", java.sql.Timestamp.from(end))
                .update();
    }

    /** Seeds a minimal proposal row (e.g. for a session FK) with given id. */
    public static void seedProposal(JdbcClient jdbc, String proposalId, String intentIdA, String intentIdB,
                                    String hotspotId, java.time.Instant start, java.time.Instant end) {
        jdbc.sql("""
                        INSERT INTO match_proposal (
                            proposal_id, intent_id_a, intent_id_b, proposed_start_time, proposed_end_time,
                            hotspot_id, accepted_by_a, accepted_by_b, status, created_at, expires_at, version
                        )
                        VALUES (
                            :proposalId, :intentIdA, :intentIdB, :start, :end,
                            :hotspotId, false, false, 'PENDING', now(), now() + interval '5 minutes', 0
                        )
                        ON CONFLICT (proposal_id) DO NOTHING
                        """)
                .param("proposalId", UUID.fromString(proposalId))
                .param("intentIdA", UUID.fromString(intentIdA))
                .param("intentIdB", UUID.fromString(intentIdB))
                .param("hotspotId", UUID.fromString(hotspotId))
                .param("start", java.sql.Timestamp.from(start))
                .param("end", java.sql.Timestamp.from(end))
                .update();
    }

    // ── Final-state assertions ────────────────────────────────────────────────

    public static void resetDatabase(JdbcClient jdbc) {
        jdbc.sql("""
                        TRUNCATE TABLE walk_session, match_proposal, walk_intent,
                                     friendship, user_profile, user_account, hotspot CASCADE
                        """)
                .update();
    }

    public static long countIntents(JdbcClient jdbc) {
        return jdbc.sql("SELECT COUNT(*) FROM walk_intent").query(Long.class).single();
    }

    public static long countIntentsByStatus(JdbcClient jdbc, String status) {
        return jdbc.sql("SELECT COUNT(*) FROM walk_intent WHERE status = CAST(:status AS intent_status)")
                .param("status", status)
                .query(Long.class)
                .single();
    }

    public static long countActiveIntentsForUser(JdbcClient jdbc, String userId) {
        return jdbc.sql("""
                        SELECT COUNT(*) FROM walk_intent
                        WHERE user_id = :userId AND status IN ('OPEN', 'MATCHING')
                        """)
                .param("userId", UUID.fromString(userId))
                .query(Long.class)
                .single();
    }

    public static long countProposals(JdbcClient jdbc) {
        return jdbc.sql("SELECT COUNT(*) FROM match_proposal").query(Long.class).single();
    }

    public static long countPendingProposals(JdbcClient jdbc) {
        return jdbc.sql("SELECT COUNT(*) FROM match_proposal WHERE status = 'PENDING'")
                .query(Long.class)
                .single();
    }

    public static long countSessions(JdbcClient jdbc) {
        return jdbc.sql("SELECT COUNT(*) FROM walk_session").query(Long.class).single();
    }

    public static String proposalStatus(JdbcClient jdbc, String proposalId) {
        return jdbc.sql("SELECT status::text FROM match_proposal WHERE proposal_id = :id")
                .param("id", UUID.fromString(proposalId))
                .query(String.class)
                .optional()
                .orElse(null);
    }

    public static String intentStatus(JdbcClient jdbc, String intentId) {
        return jdbc.sql("SELECT status::text FROM walk_intent WHERE intent_id = :id")
                .param("id", UUID.fromString(intentId))
                .query(String.class)
                .optional()
                .orElse(null);
    }

    public static List<String> pendingProposalIdsForUser(JdbcClient jdbc, String userId) {
        return jdbc.sql("""
                        SELECT mp.proposal_id::text
                        FROM match_proposal mp
                        JOIN walk_intent wi_a ON wi_a.intent_id = mp.intent_id_a
                        JOIN walk_intent wi_b ON wi_b.intent_id = mp.intent_id_b
                        WHERE mp.status = 'PENDING'
                          AND (wi_a.user_id = :userId OR wi_b.user_id = :userId)
                        """)
                .param("userId", UUID.fromString(userId))
                .query((rs, rowNum) -> rs.getString("proposal_id"))
                .list();
    }
}
