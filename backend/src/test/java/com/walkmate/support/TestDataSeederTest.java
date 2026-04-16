package com.walkmate.support;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P0-5 acceptance tests — verifies every method on {@link TestDataSeeder}.
 *
 * <h3>Scope</h3>
 * <ul>
 *   <li>{@code seedHotspot} — inserts a row and returns its UUID.</li>
 *   <li>{@code seedAcceptedFriendship} — inserts a friendship with ACCEPTED status.</li>
 *   <li>{@code rewindSessionStartedAt} — moves {@code started_at} into the past.</li>
 *   <li>{@code expireProposal} — moves {@code expires_at} into the past.</li>
 * </ul>
 *
 * <p>Tests for {@code rewindSessionStartedAt} and {@code expireProposal} seed
 * the minimal required DB chain (user → hotspot → intent → proposal → session)
 * via raw JDBC inserts, bypassing the HTTP stack intentionally — the goal here
 * is to verify the UPDATE methods, not the full API flow.
 */
class TestDataSeederTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // ── seedHotspot ───────────────────────────────────────────────────────────

    @Test
    void seedHotspot_insertsRow_andReturnsUuid() {
        String id = dataSeeder.seedHotspot("Central Park Test", 10.775, 106.700);

        assertThat(id).isNotBlank();

        String name = jdbcTemplate.queryForObject(
                "SELECT name FROM public.hotspot WHERE id = ?::uuid",
                String.class, id);
        assertThat(name).isEqualTo("Central Park Test");
    }

    @Test
    void seedHotspot_defaultOverload_insertsRow() {
        String id = dataSeeder.seedHotspot();

        assertThat(id).isNotBlank();
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public.hotspot WHERE id = ?::uuid",
                Integer.class, id);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void seedHotspot_twoCallsInSameTest_produceDifferentIds() {
        String idA = dataSeeder.seedHotspot("Spot A", 10.77, 106.70);
        String idB = dataSeeder.seedHotspot("Spot B", 10.78, 106.71);

        assertThat(idA).isNotEqualTo(idB);
    }

    // ── seedAcceptedFriendship ────────────────────────────────────────────────

    @Test
    void seedAcceptedFriendship_insertsFriendshipWithAcceptedStatus() throws Exception {
        // Create two real users via HTTP stack
        String tokenA = authFactory.createAndLoginUser("friend-a@example.com", "Password1!");
        String tokenB = authFactory.createAndLoginUser("friend-b@example.com", "Password1!");

        String userIdA = resolveUserId("friend-a@example.com");
        String userIdB = resolveUserId("friend-b@example.com");

        dataSeeder.seedAcceptedFriendship(userIdA, userIdB);

        String status = jdbcTemplate.queryForObject(
                """
                SELECT status::text FROM public.friendship
                WHERE requester_id = ?::uuid AND addressee_id = ?::uuid
                """,
                String.class, userIdA, userIdB);
        assertThat(status).isEqualTo("ACCEPTED");

        // Suppress unused-variable warning: tokens confirm users were created
        assertThat(tokenA).startsWith("Bearer ");
        assertThat(tokenB).startsWith("Bearer ");
    }

    // ── rewindSessionStartedAt ────────────────────────────────────────────────

    @Test
    void rewindSessionStartedAt_movesStartedAtIntoPast() throws Exception {
        // Seed the full chain needed for a walk_session row
        String hotspotId  = dataSeeder.seedHotspot();
        String userIdA    = createUserAndGetId("session-user-a@example.com");
        String userIdB    = createUserAndGetId("session-user-b@example.com");
        String intentIdA  = seedMinimalIntent(hotspotId, userIdA);
        String intentIdB  = seedMinimalIntent(hotspotId, userIdB);
        String proposalId = seedMinimalProposal(intentIdA, intentIdB);
        String sessionId  = seedMinimalSession(proposalId, userIdA, userIdB);

        Instant before = Instant.now();
        dataSeeder.rewindSessionStartedAt(sessionId, Duration.ofMinutes(30));

        Instant startedAt = jdbcTemplate.queryForObject(
                "SELECT started_at FROM public.walk_session WHERE session_id = ?::uuid",
                Instant.class, sessionId);

        assertThat(startedAt).isBefore(before.minusSeconds(25 * 60)); // at least 25 min ago
    }

    // ── expireProposal ────────────────────────────────────────────────────────

    @Test
    void expireProposal_movesExpiresAtIntoPast() throws Exception {
        String hotspotId  = dataSeeder.seedHotspot();
        String userIdA    = createUserAndGetId("proposal-user-a@example.com");
        String userIdB    = createUserAndGetId("proposal-user-b@example.com");
        String intentIdA  = seedMinimalIntent(hotspotId, userIdA);
        String intentIdB  = seedMinimalIntent(hotspotId, userIdB);
        String proposalId = seedMinimalProposal(intentIdA, intentIdB);

        dataSeeder.expireProposal(proposalId);

        Instant expiresAt = jdbcTemplate.queryForObject(
                "SELECT expires_at FROM public.match_proposal WHERE proposal_id = ?::uuid",
                Instant.class, proposalId);

        assertThat(expiresAt).isBefore(Instant.now());
    }

    // ── Private chain-building helpers ────────────────────────────────────────

    private String resolveUserId(String email) {
        return jdbcTemplate.queryForObject(
                "SELECT user_id::text FROM public.user_account WHERE email = ?",
                String.class, email);
    }

    private String createUserAndGetId(String email) throws Exception {
        authFactory.createAndLoginUser(email, "Password1!");
        return resolveUserId(email);
    }

    private String seedMinimalIntent(String hotspotId, String userId) {
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO public.walk_intent
                    (hotspot_id, user_id, time_window_start, time_window_end, expires_at)
                VALUES
                    (?::uuid, ?::uuid,
                     now() + interval '1 hour',
                     now() + interval '2 hours',
                     now() + interval '2 hours')
                RETURNING intent_id::text
                """,
                String.class, hotspotId, userId);
    }

    private String seedMinimalProposal(String intentIdA, String intentIdB) {
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO public.match_proposal
                    (intent_id_a, intent_id_b,
                     proposed_start_time, proposed_end_time,
                     proposed_location_lat, proposed_location_lng,
                     expires_at)
                VALUES
                    (?::uuid, ?::uuid,
                     now() + interval '1 hour',
                     now() + interval '2 hours',
                     10.775, 106.700,
                     now() + interval '30 minutes')
                RETURNING proposal_id::text
                """,
                String.class, intentIdA, intentIdB);
    }

    private String seedMinimalSession(String proposalId, String userIdA, String userIdB) {
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO public.walk_session
                    (proposal_id, user_id_a, user_id_b,
                     meeting_point_lat, meeting_point_lng,
                     scheduled_start, scheduled_end,
                     started_at)
                VALUES
                    (?::uuid, ?::uuid, ?::uuid,
                     10.775, 106.700,
                     now() + interval '1 hour',
                     now() + interval '2 hours',
                     now())
                RETURNING session_id::text
                """,
                String.class, proposalId, userIdA, userIdB);
    }
}
