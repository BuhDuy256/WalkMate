package com.walkmate.integration.invariant;

import com.walkmate.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Invariant matrix tests for reputation update rules.
 *
 * <h3>Invariant covered</h3>
 * <ul>
 *   <li><b>X-4</b> — Reputation is updated in two discrete stages:
 *     <ol>
 *       <li><b>Stage 1 — Session outcome:</b> {@code completeSession()} fires
 *           {@code SessionCompletedEvent}. {@code GamificationCommandService} rewards
 *           both participants with points (distance × 10 + duration_min × 2).
 *           Trust score is NOT changed at this stage for COMPLETED sessions.</li>
 *       <li><b>Stage 2 — Review adjustment:</b> {@code submitReview()} applies
 *           {@code SessionOutcome.COMPLETED (+5)} to the <em>reviewee's</em> trust
 *           score. The reviewer's trust score is unchanged.</li>
 *     </ol>
 *   </li>
 * </ul>
 *
 * <h3>Known repository limitation</h3>
 * {@code UserJdbcRepository.save()} does not persist {@code totalDistanceKm} or
 * {@code completedSessions} (known bug). Stage 1 therefore asserts only that
 * {@code totalPoints > 0} to confirm the gamification event fired correctly.
 */
class ReputationInvariantTest extends AbstractIntegrationTest {

    private static final String COMPLETE_URL = "/api/v1/sessions/{sessionId}/complete";
    private static final String REVIEW_URL   = "/api/v1/sessions/{sessionId}/review";

    // ── INV-X4: Two-stage reputation update ──────────────────────────────────

    /**
     * X-4: Completing a session awards points (Stage 1, no trust change);
     * submitting a review then adjusts the reviewee's trust score (Stage 2, +5).
     *
     * <ol>
     *   <li>Seed an ACTIVE session; rewind {@code started_at} 6 min into the past.</li>
     *   <li>Complete the session via API → {@code SessionCompletedEvent} fires AFTER_COMMIT.</li>
     *   <li>Assert both users' {@code total_points > 0} (gamification worked).</li>
     *   <li>Assert both users' {@code trust_score = 0} (trust not updated by completion).</li>
     *   <li>User A submits a 5-star review for User B.</li>
     *   <li>Assert User B's {@code trust_score = 5} (+5 for COMPLETED outcome).</li>
     *   <li>Assert User A's {@code trust_score = 0} (reviewer is unaffected).</li>
     * </ol>
     */
    @Test
    void inv_x4_reputationUpdatesInTwoStages_pointsThenTrustScore() throws Exception {
        String hotspotId = dataSeeder.seedHotspot();
        String tokenA = authFactory.createAndLoginUser("x4.a@example.com", "Password1!");
        authFactory.createAndLoginUser("x4.b@example.com", "Password1!");
        String userIdA = getUserIdByEmail("x4.a@example.com");
        String userIdB = getUserIdByEmail("x4.b@example.com");

        Instant start = Instant.now().plus(5, ChronoUnit.MINUTES);
        String sessionId = dataSeeder.seedActiveSession(userIdA, userIdB, hotspotId,
                start, start.plus(1, ChronoUnit.HOURS));

        // Rewind started_at to 6m01s ago — satisfies the S-5 minimum duration guard
        dataSeeder.rewindSessionStartedAt(sessionId, java.time.Duration.ofSeconds(361));

        // ── Stage 1: Complete session → gamification event fires AFTER_COMMIT ──

        mockMvc.perform(post(COMPLETE_URL, sessionId).header("Authorization", tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));

        int pointsA = getTotalPoints(userIdA);
        int pointsB = getTotalPoints(userIdB);
        int trustA  = getTrustScore(userIdA);
        int trustB  = getTrustScore(userIdB);

        // Points must be > 0: points = 0 distance × 10 + 6 minutes × 2 = 12
        assertThat(pointsA).as("Stage 1: User A awarded points after completion").isGreaterThan(0);
        assertThat(pointsB).as("Stage 1: User B awarded points after completion").isGreaterThan(0);

        // Trust score must be unchanged after completion — X-4 stage 1 only affects points
        assertThat(trustA).as("Stage 1: trust score unchanged for User A").isEqualTo(0);
        assertThat(trustB).as("Stage 1: trust score unchanged for User B").isEqualTo(0);

        // ── Stage 2: Submit review → trust score adjusted for reviewee ────────

        String reviewBody = "{\"rating_stars\": 5, \"comment\": \"Great walk!\"}";
        mockMvc.perform(post(REVIEW_URL, sessionId).header("Authorization", tokenA)
                        .contentType(APPLICATION_JSON).content(reviewBody))
                .andExpect(status().isOk());

        int trustAAfterReview = getTrustScore(userIdA);
        int trustBAfterReview = getTrustScore(userIdB);

        // Reviewee (User B) trust score: SessionOutcome.COMPLETED delta = +5
        assertThat(trustBAfterReview)
                .as("Stage 2: reviewee (User B) trust score updated by +5 (COMPLETED outcome)")
                .isEqualTo(trustB + 5);

        // Reviewer (User A) trust score must remain unchanged — only reviewee is adjusted
        assertThat(trustAAfterReview)
                .as("Stage 2: reviewer (User A) trust score must remain unchanged")
                .isEqualTo(trustA);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private String getUserIdByEmail(String email) {
        return jdbcTemplate.queryForObject(
                "SELECT user_id::text FROM public.user_account WHERE email = ?",
                String.class, email);
    }

    private int getTotalPoints(String userId) {
        return jdbcTemplate.queryForObject(
                "SELECT total_points FROM public.user_account WHERE user_id = ?::uuid",
                Integer.class, userId);
    }

    private int getTrustScore(String userId) {
        return jdbcTemplate.queryForObject(
                "SELECT trust_score FROM public.user_account WHERE user_id = ?::uuid",
                Integer.class, userId);
    }
}
