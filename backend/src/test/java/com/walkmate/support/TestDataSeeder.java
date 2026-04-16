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
