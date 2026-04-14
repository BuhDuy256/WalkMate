package com.walkmate.support;

import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;

/**
 * Integration-test helper for seeding DB state that cannot be established through
 * the public HTTP API (e.g. hotspot rows, friendship status, timestamp manipulation).
 *
 * <p>This is a plain utility class — not a Spring bean. Obtain an instance via
 * {@link AbstractIntegrationTest#dataSeeder}, which is re-created before each test.
 *
 * <h3>Design contract</h3>
 * <ul>
 *   <li>Every method is a thin wrapper around one JDBC call — no business logic.</li>
 *   <li>Callers are responsible for ensuring FK prerequisites exist before seeding
 *       dependent rows (e.g. users must exist before calling
 *       {@link #seedAcceptedFriendship}).</li>
 *   <li>All UUID parameters are accepted as {@code String} for consistency with
 *       {@link AuthTokenFactory} token payloads and test assertions.</li>
 * </ul>
 */
public class TestDataSeeder {

    private final JdbcTemplate jdbc;

    public TestDataSeeder(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ── Hotspot ───────────────────────────────────────────────────────────────

    /**
     * Inserts a hotspot row and returns its generated UUID.
     *
     * <p>The returned ID can be passed directly into intent-creation API calls
     * as the {@code hotspotId} field — all Intent tests require at least one
     * hotspot to exist in the DB.
     *
     * @param name human-readable hotspot name
     * @param lat  latitude (decimal degrees)
     * @param lng  longitude (decimal degrees)
     * @return the generated UUID as a {@code String}
     */
    public String seedHotspot(String name, double lat, double lng) {
        return jdbc.queryForObject(
                """
                INSERT INTO public.hotspot (name, lat, lng)
                VALUES (?, ?, ?)
                RETURNING id::text
                """,
                String.class,
                name, lat, lng
        );
    }

    /**
     * Inserts a hotspot with sensible Ho Chi Minh City defaults.
     * Use when the test does not care about the exact location.
     *
     * @return the generated UUID as a {@code String}
     */
    public String seedHotspot() {
        return seedHotspot("Test Hotspot", 10.775, 106.700);
    }

    // ── Walk Session (direct seed) ────────────────────────────────────────────

    /**
     * Seeds a {@code PENDING} walk session for the given pair of users, bypassing the full
     * match-proposal API flow.
     *
     * <p>Inserts the minimum FK chain:
     * <ol>
     *   <li>Two {@code walk_intent} rows (OPEN, one per user) referencing {@code hotspotId}.</li>
     *   <li>One {@code match_proposal} row (CONFIRMED) referencing those intents.</li>
     *   <li>One {@code walk_session} row (PENDING) referencing the proposal.</li>
     * </ol>
     *
     * <p>Used by T15-3 to assert that {@code POST /intents} returns
     * {@code INTENT_OVERLAPPING_SESSION} when a user already has a PENDING session
     * that overlaps the requested time window.
     *
     * @param userIdA       UUID string of the first participant (the user under test)
     * @param userIdB       UUID string of the second participant (placeholder)
     * @param hotspotId     UUID string of the hotspot (must already exist in DB)
     * @param scheduledStart overlapping window start (inclusive)
     * @param scheduledEnd   overlapping window end (exclusive)
     */
    public void seedPendingSession(String userIdA, String userIdB, String hotspotId,
                                   java.time.Instant scheduledStart, java.time.Instant scheduledEnd) {
        java.sql.Timestamp start = java.sql.Timestamp.from(scheduledStart);
        java.sql.Timestamp end   = java.sql.Timestamp.from(scheduledEnd);
        java.sql.Timestamp far   = java.sql.Timestamp.from(scheduledEnd.plusSeconds(3600));

        // 1. Seed intent rows — use CONSUMED status so hasOverlappingActiveIntent (which checks
        //    OPEN/MATCHING) does NOT fire, allowing hasOverlappingActiveSession to be reached.
        String intentIdA = jdbc.queryForObject(
                """
                INSERT INTO public.walk_intent
                    (hotspot_id, user_id, time_window_start, time_window_end,
                     matching_constraints, expires_at, status)
                VALUES (?::uuid, ?::uuid, ?, ?, '{"age_min":18,"age_max":60}'::jsonb, ?,
                        'CONSUMED'::intent_status)
                RETURNING intent_id::text
                """,
                String.class, hotspotId, userIdA, start, end, far);

        String intentIdB = jdbc.queryForObject(
                """
                INSERT INTO public.walk_intent
                    (hotspot_id, user_id, time_window_start, time_window_end,
                     matching_constraints, expires_at, status)
                VALUES (?::uuid, ?::uuid, ?, ?, '{"age_min":18,"age_max":60}'::jsonb, ?,
                        'CONSUMED'::intent_status)
                RETURNING intent_id::text
                """,
                String.class, hotspotId, userIdB, start, end, far);

        // 2. Seed match_proposal (CONFIRMED — both accepted, session already created)
        String proposalId = jdbc.queryForObject(
                """
                INSERT INTO public.match_proposal
                    (intent_id_a, intent_id_b,
                     proposed_start_time, proposed_end_time,
                     proposed_location_lat, proposed_location_lng,
                     status, expires_at)
                VALUES (?::uuid, ?::uuid, ?, ?, 10.775, 106.700,
                        'CONFIRMED'::proposal_status, ?)
                RETURNING proposal_id::text
                """,
                String.class, intentIdA, intentIdB, start, end, far);

        // 3. Seed walk_session (PENDING — created after proposal confirmed)
        jdbc.update(
                """
                INSERT INTO public.walk_session
                    (proposal_id, user_id_a, user_id_b,
                     meeting_point_lat, meeting_point_lng,
                     scheduled_start, scheduled_end, status)
                VALUES (?::uuid, ?::uuid, ?::uuid, 10.775, 106.700, ?, ?,
                        'PENDING'::walk_session_status)
                """,
                proposalId, userIdA, userIdB, start, end);
    }

    // ── Walk Intent (direct seed) — single OPEN intent ───────────────────────

    /**
     * Inserts a single {@code OPEN} walk intent for the given user, bypassing the
     * public API and its inline-match side-effect.
     *
     * <p>Use this when you need a second intent in the DB but want to prevent the
     * inline match that fires on {@code POST /intents} — e.g. to set up the
     * candidate pool before explicitly calling {@code POST /intents/{id}/match}.
     *
     * <p>{@code expires_at} is set to {@code timeWindowEnd} (same as
     * {@link com.walkmate.domain.walkintent.WalkIntent#create}).
     *
     * @param userId          UUID string of the intent owner (must exist in DB)
     * @param hotspotId       UUID string of the hotspot (must exist in DB)
     * @param timeWindowStart start of the walk window
     * @param timeWindowEnd   end of the walk window
     * @param ageMin          minimum preferred partner age
     * @param ageMax          maximum preferred partner age
     * @return the generated intent UUID as a {@code String}
     */
    public String seedOpenIntent(String userId, String hotspotId,
                                 java.time.Instant timeWindowStart, java.time.Instant timeWindowEnd,
                                 int ageMin, int ageMax) {
        java.sql.Timestamp start       = java.sql.Timestamp.from(timeWindowStart);
        java.sql.Timestamp end         = java.sql.Timestamp.from(timeWindowEnd);
        String constraints = String.format("{\"age_min\":%d,\"age_max\":%d}", ageMin, ageMax);

        return jdbc.queryForObject(
                """
                INSERT INTO public.walk_intent
                    (hotspot_id, user_id, time_window_start, time_window_end,
                     matching_constraints, expires_at, status, is_private)
                VALUES (?::uuid, ?::uuid, ?, ?,
                        ?::jsonb, ?,
                        'OPEN'::intent_status, false)
                RETURNING intent_id::text
                """,
                String.class,
                hotspotId, userId, start, end, constraints, end
        );
    }

    /**
     * Bypasses domain logic to force an intent into an arbitrary {@code intent_status}.
     *
     * <p>Use sparingly — only in tests that need to start from a specific terminal or
     * intermediate state that is impossible to reach via the public API alone
     * (e.g. {@code CONSUMED} to test {@code INTENT_ALREADY_CONSUMED}, or
     * {@code MATCHING} to test {@code INVALID_INTENT_DATA} on the match endpoint).
     *
     * @param intentId UUID string of the target intent
     * @param status   target status string (e.g. {@code "CONSUMED"}, {@code "MATCHING"})
     */
    public void forceIntentStatus(String intentId, String status) {
        jdbc.update(
                "UPDATE public.walk_intent SET status = ?::intent_status WHERE intent_id = ?::uuid",
                status, intentId
        );
    }

    // ── Friendship ────────────────────────────────────────────────────────────

    /**
     * Inserts an {@code ACCEPTED} friendship record between two existing users.
     *
     * <p>Use this to satisfy the "mutual friends" prerequisite for private-intent
     * tests (I-7), where the invited user must already be a friend of the intent owner.
     *
     * <p><strong>Prerequisite:</strong> both user IDs must already exist in
     * {@code public.user_account} (create them via {@link AuthTokenFactory} first).
     *
     * @param requesterUserId UUID of the user who sent the friend request
     * @param addresseeUserId UUID of the user who received (and accepted) it
     */
    public void seedAcceptedFriendship(String requesterUserId, String addresseeUserId) {
        jdbc.update(
                """
                INSERT INTO public.friendship (requester_id, addressee_id, status)
                VALUES (?::uuid, ?::uuid, 'ACCEPTED'::friend_status)
                """,
                requesterUserId, addresseeUserId
        );
    }

    // ── Timestamp manipulation ────────────────────────────────────────────────

    /**
     * Moves {@code walk_session.started_at} backward in time by the given duration.
     *
     * <p>Used in session-lifecycle tests (e.g. S-5) that require a session to have
     * been "running" for a minimum amount of time before the session-end endpoint
     * accepts the request. Call this after the session transitions to {@code ACTIVE}
     * status via the normal API flow.
     *
     * @param sessionId UUID of the walk session to backdate
     * @param ago       how far back to move {@code started_at}
     */
    public void rewindSessionStartedAt(String sessionId, Duration ago) {
        long seconds = ago.toSeconds();
        jdbc.update(
                """
                UPDATE public.walk_session
                SET started_at = now() - (? * interval '1 second')
                WHERE session_id = ?::uuid
                """,
                seconds, sessionId
        );
    }

    /**
     * Sets {@code match_proposal.expires_at} to one second in the past, making the
     * proposal definitively expired.
     *
     * <p>Used in proposal TTL tests (e.g. P-4) that assert acceptance is rejected
     * once the proposal window has closed. Call this after a proposal is created
     * via the normal matching flow.
     *
     * @param proposalId UUID of the match proposal to expire
     */
    public void expireProposal(String proposalId) {
        jdbc.update(
                """
                UPDATE public.match_proposal
                SET expires_at = now() - interval '1 second'
                WHERE proposal_id = ?::uuid
                """,
                proposalId
        );
    }
}
