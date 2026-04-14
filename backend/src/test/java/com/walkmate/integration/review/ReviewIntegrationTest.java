package com.walkmate.integration.review;

import com.walkmate.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the review submission endpoint.
 *
 * <h3>Use cases covered</h3>
 * <ul>
 *   <li><b>UC-31 Submit a Review</b> — T31-1 through T31-4</li>
 * </ul>
 *
 * <h3>Trust score note (Invariant X-4)</h3>
 * Session outcome baseline adjustment fires when the session reaches a terminal
 * state. The review adjustment fires here via {@code ReviewCommandService}.
 * T31-1 only verifies the review-stage delta on the reviewee's trust score.
 */
class ReviewIntegrationTest extends AbstractIntegrationTest {

    private static final String REVIEW_URL = "/api/v1/sessions/{sessionId}/review";

    // ── T31-1: Happy Path ─────────────────────────────────────────────────────

    /**
     * T31-1: User A submits a 5-star review for a COMPLETED session.
     * Review row is persisted in DB; reviewee (User B) trust score is updated.
     */
    @Test
    void t31_1_submitReview_happyPath_reviewPersistedAndTrustScoreUpdated() throws Exception {
        String hotspotId = dataSeeder.seedHotspot();
        Instant start = Instant.now().plus(5, ChronoUnit.MINUTES);
        Instant end   = start.plus(1, ChronoUnit.HOURS);

        String tokenA = authFactory.createAndLoginUser("review.t311.a@example.com", "Password1!");
        authFactory.createAndLoginUser("review.t311.b@example.com", "Password1!");

        String userIdA = getUserIdByEmail("review.t311.a@example.com");
        String userIdB = getUserIdByEmail("review.t311.b@example.com");

        String sessionId = seedCompletedSession(userIdA, userIdB, hotspotId, start, end);

        // Capture User B's trust score before review
        int trustBefore = jdbcTemplate.queryForObject(
                "SELECT trust_score FROM public.user_account WHERE user_id = ?::uuid",
                Integer.class, userIdB);

        String body = """
                { "rating_stars": 5, "comment": "Great walk!" }
                """;

        mockMvc.perform(post(REVIEW_URL, sessionId)
                        .header("Authorization", tokenA)
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.review_id").isNotEmpty())
                .andExpect(jsonPath("$.data.session_id").value(sessionId))
                .andExpect(jsonPath("$.data.rating_stars").value(5));

        // DB: review row exists
        Integer reviewCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public.walk_review " +
                "WHERE session_id = ?::uuid AND reviewer_id = ?::uuid",
                Integer.class, sessionId, userIdA);
        assertThat(reviewCount).isEqualTo(1);

        // DB: User B's trust score changed (review adjustment applied)
        int trustAfter = jdbcTemplate.queryForObject(
                "SELECT trust_score FROM public.user_account WHERE user_id = ?::uuid",
                Integer.class, userIdB);
        assertThat(trustAfter).isNotEqualTo(trustBefore);
    }

    // ── T31-2: Duplicate Review ───────────────────────────────────────────────

    /**
     * T31-2: Submitting a second review for the same session returns
     * {@code REVIEW_ALREADY_SUBMITTED} (HTTP 400).
     */
    @Test
    void t31_2_submitReview_duplicate_returnsReviewAlreadySubmitted() throws Exception {
        String hotspotId = dataSeeder.seedHotspot();
        Instant start = Instant.now().plus(5, ChronoUnit.MINUTES);
        Instant end   = start.plus(1, ChronoUnit.HOURS);

        String tokenA = authFactory.createAndLoginUser("review.t312.a@example.com", "Password1!");
        authFactory.createAndLoginUser("review.t312.b@example.com", "Password1!");

        String userIdA = getUserIdByEmail("review.t312.a@example.com");
        String userIdB = getUserIdByEmail("review.t312.b@example.com");

        String sessionId = seedCompletedSession(userIdA, userIdB, hotspotId, start, end);

        String body = """
                { "rating_stars": 4 }
                """;

        // First submission — succeeds
        mockMvc.perform(post(REVIEW_URL, sessionId)
                        .header("Authorization", tokenA)
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        // Second submission — rejected
        mockMvc.perform(post(REVIEW_URL, sessionId)
                        .header("Authorization", tokenA)
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("REVIEW_ALREADY_SUBMITTED"));
    }

    // ── T31-3: Session Not Completed ──────────────────────────────────────────

    /**
     * T31-3: Submitting a review on an ABORTED session returns
     * {@code REVIEW_SESSION_NOT_COMPLETED} (HTTP 400).
     */
    @Test
    void t31_3_submitReview_sessionAborted_returnsReviewSessionNotCompleted() throws Exception {
        String hotspotId = dataSeeder.seedHotspot();
        Instant start = Instant.now().plus(5, ChronoUnit.MINUTES);
        Instant end   = start.plus(1, ChronoUnit.HOURS);

        String tokenA = authFactory.createAndLoginUser("review.t313.a@example.com", "Password1!");
        authFactory.createAndLoginUser("review.t313.b@example.com", "Password1!");

        String userIdA = getUserIdByEmail("review.t313.a@example.com");
        String userIdB = getUserIdByEmail("review.t313.b@example.com");

        String sessionId = dataSeeder.seedActiveSession(userIdA, userIdB, hotspotId, start, end);
        jdbcTemplate.update(
                "UPDATE public.walk_session " +
                "SET status = 'ABORTED'::walk_session_status, ended_at = now() " +
                "WHERE session_id = ?::uuid",
                sessionId);

        String body = """
                { "rating_stars": 3 }
                """;

        mockMvc.perform(post(REVIEW_URL, sessionId)
                        .header("Authorization", tokenA)
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("REVIEW_SESSION_NOT_COMPLETED"));
    }

    // ── T31-4: Invalid Rating ─────────────────────────────────────────────────

    /**
     * T31-4: Submitting {@code rating_stars: 6} fails validation.
     *
     * <p>The DTO uses {@code @Max(5)} — Bean Validation fires at the controller
     * layer and returns HTTP 422 {@code VALIDATION_ERROR}, not HTTP 400
     * {@code REVIEW_INVALID_RATING}. The service-level error code is unreachable
     * when DTO validation is active.</p>
     */
    @Test
    void t31_4_submitReview_invalidRating_returnsValidationError() throws Exception {
        String hotspotId = dataSeeder.seedHotspot();
        Instant start = Instant.now().plus(5, ChronoUnit.MINUTES);
        Instant end   = start.plus(1, ChronoUnit.HOURS);

        String tokenA = authFactory.createAndLoginUser("review.t314.a@example.com", "Password1!");
        authFactory.createAndLoginUser("review.t314.b@example.com", "Password1!");

        String userIdA = getUserIdByEmail("review.t314.a@example.com");
        String userIdB = getUserIdByEmail("review.t314.b@example.com");

        String sessionId = seedCompletedSession(userIdA, userIdB, hotspotId, start, end);

        String body = """
                { "rating_stars": 6 }
                """;

        mockMvc.perform(post(REVIEW_URL, sessionId)
                        .header("Authorization", tokenA)
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private String getUserIdByEmail(String email) {
        return jdbcTemplate.queryForObject(
                "SELECT user_id::text FROM public.user_account WHERE email = ?",
                String.class, email);
    }

    /** Seeds an ACTIVE session then promotes it to COMPLETED with stats via JDBC. */
    private String seedCompletedSession(String userIdA, String userIdB,
                                        String hotspotId, Instant start, Instant end) {
        String sessionId = dataSeeder.seedActiveSession(userIdA, userIdB, hotspotId, start, end);
        jdbcTemplate.update(
                "UPDATE public.walk_session " +
                "SET status = 'COMPLETED'::walk_session_status, " +
                "    ended_at = now(), " +
                "    total_distance_km = 2.5, " +
                "    total_duration_seconds = 1800 " +
                "WHERE session_id = ?::uuid",
                sessionId);
        return sessionId;
    }
}
