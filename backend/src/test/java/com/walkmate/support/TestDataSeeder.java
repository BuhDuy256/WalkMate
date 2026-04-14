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

    /**
     * Carries the generated IDs of a directly-seeded {@code PENDING} match proposal
     * and its two {@code MATCHING} walk intents.
     *
     * <p>Returned by {@link #seedPendingProposal} and
     * {@link #seedPendingPrivateProposal} so callers can reference individual IDs
     * in API paths and JDBC assertions without re-querying the DB.
     */
    public record ProposalSeed(
            String proposalId,
            String intentIdA,
            String intentIdB,
            String userIdA,
            String userIdB
    ) {}

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
     *   <li>Two {@code walk_intent} rows (CONSUMED, one per user) referencing {@code hotspotId}.</li>
     *   <li>One {@code match_proposal} row (CONFIRMED) referencing those intents.</li>
     *   <li>One {@code walk_session} row (PENDING) referencing the proposal.</li>
     * </ol>
     *
     * @param userIdA        UUID string of the first participant
     * @param userIdB        UUID string of the second participant
     * @param hotspotId      UUID string of the hotspot (must already exist in DB)
     * @param scheduledStart walk window start
     * @param scheduledEnd   walk window end
     * @return the generated session UUID as a {@code String}
     */
    public String seedPendingSession(String userIdA, String userIdB, String hotspotId,
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
        return jdbc.queryForObject(
                """
                INSERT INTO public.walk_session
                    (proposal_id, user_id_a, user_id_b,
                     meeting_point_lat, meeting_point_lng,
                     scheduled_start, scheduled_end, status)
                VALUES (?::uuid, ?::uuid, ?::uuid, 10.775, 106.700, ?, ?,
                        'PENDING'::walk_session_status)
                RETURNING session_id::text
                """,
                String.class, proposalId, userIdA, userIdB, start, end);
    }

    /**
     * Seeds an {@code ACTIVE} walk session for the given pair of users.
     *
     * <p>Builds the same FK chain as {@link #seedPendingSession}, then immediately
     * promotes the session to {@code ACTIVE} by setting both activation timestamps
     * and {@code started_at = now()}.
     *
     * <p>Use {@link #rewindSessionStartedAt} afterward if the test requires
     * the walk to have been running for a specific duration (e.g. S-5 minimum
     * 5-minute guard on {@code completeSession}).
     *
     * @param userIdA        UUID string of the first participant
     * @param userIdB        UUID string of the second participant
     * @param hotspotId      UUID string of the hotspot (must already exist in DB)
     * @param scheduledStart walk window start
     * @param scheduledEnd   walk window end
     * @return the generated session UUID as a {@code String}
     */
    public String seedActiveSession(String userIdA, String userIdB, String hotspotId,
                                    java.time.Instant scheduledStart, java.time.Instant scheduledEnd) {
        String sessionId = seedPendingSession(userIdA, userIdB, hotspotId, scheduledStart, scheduledEnd);
        jdbc.update(
                """
                UPDATE public.walk_session
                SET status               = 'ACTIVE'::walk_session_status,
                    user_a_activated_at  = now(),
                    user_b_activated_at  = now(),
                    started_at           = now()
                WHERE session_id = ?::uuid
                """,
                sessionId);
        return sessionId;
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

    // ── Match Proposal (direct seed) ─────────────────────────────────────────

    /**
     * Seeds a {@code PENDING} public match proposal for the given pair of users,
     * bypassing the full intent-creation and matching-engine API flow.
     *
     * <p>Inserts the minimum FK chain:
     * <ol>
     *   <li>Two {@code walk_intent} rows ({@code MATCHING, is_private = false},
     *       one per user) referencing {@code hotspotId}.</li>
     *   <li>One {@code match_proposal} row ({@code PENDING}, {@code expires_at = now() + 10 min})
     *       referencing those intents.</li>
     * </ol>
     *
     * <p>The intents are seeded as {@code MATCHING} (not {@code OPEN}) because
     * {@link com.walkmate.application.proposal.MatchingCommandService#acceptProposal}
     * re-verifies both intents are {@code MATCHING} before creating the session.
     *
     * @param userIdA       UUID string of user A
     * @param userIdB       UUID string of user B
     * @param hotspotId     UUID string of an existing hotspot
     * @param scheduledStart walk window start
     * @param scheduledEnd   walk window end
     * @return {@link ProposalSeed} containing all generated IDs
     */
    public ProposalSeed seedPendingProposal(String userIdA, String userIdB, String hotspotId,
                                             java.time.Instant scheduledStart,
                                             java.time.Instant scheduledEnd) {
        return seedProposal(userIdA, userIdB, hotspotId, scheduledStart, scheduledEnd, false);
    }

    /**
     * Seeds a {@code PENDING} <em>private</em> match proposal for the given pair of users.
     *
     * <p>Identical to {@link #seedPendingProposal} but both walk intents are seeded
     * with {@code is_private = true}.  Used by T21-3 to verify that passing a private
     * invite cancels both intents instead of unlocking them to {@code OPEN}.
     *
     * @param userIdA       UUID string of the inviting user
     * @param userIdB       UUID string of the invited user
     * @param hotspotId     UUID string of an existing hotspot
     * @param scheduledStart walk window start
     * @param scheduledEnd   walk window end
     * @return {@link ProposalSeed} containing all generated IDs
     */
    public ProposalSeed seedPendingPrivateProposal(String userIdA, String userIdB, String hotspotId,
                                                    java.time.Instant scheduledStart,
                                                    java.time.Instant scheduledEnd) {
        return seedProposal(userIdA, userIdB, hotspotId, scheduledStart, scheduledEnd, true);
    }

    private ProposalSeed seedProposal(String userIdA, String userIdB, String hotspotId,
                                       java.time.Instant scheduledStart,
                                       java.time.Instant scheduledEnd,
                                       boolean isPrivate) {
        java.sql.Timestamp start = java.sql.Timestamp.from(scheduledStart);
        java.sql.Timestamp end   = java.sql.Timestamp.from(scheduledEnd);

        String intentIdA = jdbc.queryForObject(
                """
                INSERT INTO public.walk_intent
                    (hotspot_id, user_id, time_window_start, time_window_end,
                     matching_constraints, expires_at, status, is_private)
                VALUES (?::uuid, ?::uuid, ?, ?, '{"age_min":18,"age_max":60}'::jsonb, ?,
                        'MATCHING'::intent_status, ?)
                RETURNING intent_id::text
                """,
                String.class, hotspotId, userIdA, start, end, end, isPrivate);

        String intentIdB = jdbc.queryForObject(
                """
                INSERT INTO public.walk_intent
                    (hotspot_id, user_id, time_window_start, time_window_end,
                     matching_constraints, expires_at, status, is_private)
                VALUES (?::uuid, ?::uuid, ?, ?, '{"age_min":18,"age_max":60}'::jsonb, ?,
                        'MATCHING'::intent_status, ?)
                RETURNING intent_id::text
                """,
                String.class, hotspotId, userIdB, start, end, end, isPrivate);

        String proposalId = jdbc.queryForObject(
                """
                INSERT INTO public.match_proposal
                    (proposal_id, intent_id_a, intent_id_b,
                     proposed_start_time, proposed_end_time,
                     proposed_location_lat, proposed_location_lng,
                     accepted_by_a, accepted_by_b,
                     status, created_at, expires_at, confirmed_at, version)
                VALUES (gen_random_uuid(), ?::uuid, ?::uuid, ?, ?, 10.775, 106.700,
                        false, false,
                        'PENDING'::proposal_status, now(), now() + interval '10 minutes', null, 0)
                RETURNING proposal_id::text
                """,
                String.class, intentIdA, intentIdB, start, end);

        return new ProposalSeed(proposalId, intentIdA, intentIdB, userIdA, userIdB);
    }

    /**
     * Bypasses domain logic to force a proposal into an arbitrary {@code proposal_status}.
     *
     * <p>Use sparingly — only in tests that need a specific terminal state that is
     * impossible to reach via the public API (e.g. {@code EXPIRED} to test
     * {@code PROPOSAL_ALREADY_TERMINAL} on the accept endpoint, or {@code REJECTED}).
     *
     * @param proposalId UUID string of the target proposal
     * @param status     target status string (e.g. {@code "EXPIRED"}, {@code "REJECTED"})
     */
    public void forceProposalStatus(String proposalId, String status) {
        jdbc.update(
                "UPDATE public.match_proposal SET status = ?::proposal_status WHERE proposal_id = ?::uuid",
                status, proposalId
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
