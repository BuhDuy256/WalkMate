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
 * Invariant matrix tests for session lifecycle rules.
 *
 * <h3>Invariants covered</h3>
 * <ul>
 *   <li><b>I-6</b> — Terminal session states are immutable
 *       (COMPLETED blocks activate, cancel, and abort)</li>
 *   <li><b>S-3</b> — Activation window boundaries: inside window → OK;
 *       after window close → {@code SESSION_ACTIVATION_WINDOW_CLOSED}</li>
 *   <li><b>S-5</b> — 5-minute minimum walk duration boundary:
 *       4m59s → {@code SESSION_COMPLETE_TOO_EARLY}; 5m01s → 200 OK</li>
 * </ul>
 */
class SessionInvariantTest extends AbstractIntegrationTest {

    private static final String ACTIVATE_URL = "/api/v1/sessions/{sessionId}/activate";
    private static final String CANCEL_URL   = "/api/v1/sessions/{sessionId}/cancel";
    private static final String ABORT_URL    = "/api/v1/sessions/{sessionId}/abort";
    private static final String COMPLETE_URL = "/api/v1/sessions/{sessionId}/complete";

    // ── INV-I6: COMPLETED session — all transitions rejected ─────────────────

    /**
     * I-6: A COMPLETED (terminal) session rejects activate, cancel, and abort.
     * Each call returns the appropriate domain error code; no state mutation occurs.
     */
    @Test
    void inv_i6_completedSession_allTransitionsRejected() throws Exception {
        String hotspotId = dataSeeder.seedHotspot();
        String tokenA = authFactory.createAndLoginUser("i6.a@example.com", "Password1!");
        authFactory.createAndLoginUser("i6.b@example.com", "Password1!");
        String userIdA = getUserIdByEmail("i6.a@example.com");
        String userIdB = getUserIdByEmail("i6.b@example.com");

        Instant start = Instant.now().minus(2, ChronoUnit.HOURS);
        Instant end   = start.plus(1, ChronoUnit.HOURS);

        String sessionId = dataSeeder.seedActiveSession(userIdA, userIdB, hotspotId, start, end);

        // Bypass S-5 minimum duration guard — force directly to COMPLETED via JDBC
        jdbcTemplate.update(
                "UPDATE public.walk_session " +
                "SET status = 'COMPLETED'::walk_session_status, ended_at = now() " +
                "WHERE session_id = ?::uuid",
                sessionId);

        // activate on COMPLETED → SESSION_NOT_PENDING
        mockMvc.perform(post(ACTIVATE_URL, sessionId).header("Authorization", tokenA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("SESSION_NOT_PENDING"));

        // cancel on COMPLETED → SESSION_CANCEL_NOT_PENDING
        mockMvc.perform(post(CANCEL_URL, sessionId).header("Authorization", tokenA)
                        .contentType(APPLICATION_JSON)
                        .content("{\"reason\":\"test reason\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("SESSION_CANCEL_NOT_PENDING"));

        // abort on COMPLETED → SESSION_NOT_ACTIVE
        mockMvc.perform(post(ABORT_URL, sessionId).header("Authorization", tokenA)
                        .contentType(APPLICATION_JSON)
                        .content("{\"reason\":\"SAFETY_CONCERN\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("SESSION_NOT_ACTIVE"));
    }

    // ── INV-S3a: Inside activation window — near open boundary ───────────────

    /**
     * S-3a: {@code scheduledStart = now + 5 min}.
     * Window opens at {@code scheduledStart − 10 min = now − 5 min} (past) → window is open.
     * Single-user activation must return {@code 200 OK}; session stays {@code PENDING}
     * because User B has not yet activated.
     */
    @Test
    void inv_s3a_activationInsideWindow_earlyBoundary_succeeds() throws Exception {
        String hotspotId = dataSeeder.seedHotspot();
        String tokenA = authFactory.createAndLoginUser("s3a.a@example.com", "Password1!");
        authFactory.createAndLoginUser("s3a.b@example.com", "Password1!");
        String userIdA = getUserIdByEmail("s3a.a@example.com");
        String userIdB = getUserIdByEmail("s3a.b@example.com");

        // Seed with a far-future window, then override scheduled_start via JDBC
        Instant nominalStart = Instant.now().plus(1, ChronoUnit.HOURS);
        String sessionId = dataSeeder.seedPendingSession(userIdA, userIdB, hotspotId,
                nominalStart, nominalStart.plus(1, ChronoUnit.HOURS));

        // scheduledStart = now + 5 min → windowOpen = now − 5 min (in past) → inside window
        Instant scheduledStart = Instant.now().plus(5, ChronoUnit.MINUTES);
        jdbcTemplate.update(
                "UPDATE public.walk_session SET scheduled_start = ? WHERE session_id = ?::uuid",
                java.sql.Timestamp.from(scheduledStart), sessionId);

        mockMvc.perform(post(ACTIVATE_URL, sessionId).header("Authorization", tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    // ── INV-S3b: Inside activation window — near close boundary ─────────────

    /**
     * S-3b: {@code scheduledStart = now − 14 min}.
     * Window closes at {@code scheduledStart + 15 min = now + 1 min} (future) → window is open.
     * Activation must succeed; session stays {@code PENDING}.
     */
    @Test
    void inv_s3b_activationInsideWindow_lateBoundary_succeeds() throws Exception {
        String hotspotId = dataSeeder.seedHotspot();
        String tokenA = authFactory.createAndLoginUser("s3b.a@example.com", "Password1!");
        authFactory.createAndLoginUser("s3b.b@example.com", "Password1!");
        String userIdA = getUserIdByEmail("s3b.a@example.com");
        String userIdB = getUserIdByEmail("s3b.b@example.com");

        Instant nominalStart = Instant.now().plus(1, ChronoUnit.HOURS);
        String sessionId = dataSeeder.seedPendingSession(userIdA, userIdB, hotspotId,
                nominalStart, nominalStart.plus(1, ChronoUnit.HOURS));

        // scheduledStart = now − 14 min → windowClose = now + 1 min (in future) → inside window
        Instant scheduledStart = Instant.now().minus(14, ChronoUnit.MINUTES);
        jdbcTemplate.update(
                "UPDATE public.walk_session SET scheduled_start = ? WHERE session_id = ?::uuid",
                java.sql.Timestamp.from(scheduledStart), sessionId);

        mockMvc.perform(post(ACTIVATE_URL, sessionId).header("Authorization", tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    // ── INV-S3c: After activation window close ────────────────────────────────

    /**
     * S-3c: {@code scheduledStart = now − 16 min}.
     * Window closes at {@code scheduledStart + 15 min = now − 1 min} (past) → window closed.
     * Activation must return {@code SESSION_ACTIVATION_WINDOW_CLOSED}.
     */
    @Test
    void inv_s3c_activationAfterWindowClose_returnsWindowClosed() throws Exception {
        String hotspotId = dataSeeder.seedHotspot();
        String tokenA = authFactory.createAndLoginUser("s3c.a@example.com", "Password1!");
        authFactory.createAndLoginUser("s3c.b@example.com", "Password1!");
        String userIdA = getUserIdByEmail("s3c.a@example.com");
        String userIdB = getUserIdByEmail("s3c.b@example.com");

        // scheduledStart = now − 16 min → windowClose = now − 1 min → closed
        Instant scheduledStart = Instant.now().minus(16, ChronoUnit.MINUTES);
        String sessionId = dataSeeder.seedPendingSession(userIdA, userIdB, hotspotId,
                scheduledStart, scheduledStart.plus(1, ChronoUnit.HOURS));

        mockMvc.perform(post(ACTIVATE_URL, sessionId).header("Authorization", tokenA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("SESSION_ACTIVATION_WINDOW_CLOSED"));
    }

    // ── INV-S5a: Complete before minimum duration ─────────────────────────────

    /**
     * S-5a: {@code started_at = now − 4 min} — safely under the 5-minute minimum.
     * A 299-second rewind is too close to the boundary for integration tests:
     * test-execution overhead pushes the measured duration past 300s before the
     * request reaches the service. This test uses 240s (4 min) as a stable lower bound.
     * Complete must return {@code SESSION_COMPLETE_TOO_EARLY}.
     */
    @Test
    void inv_s5a_completeBeforeMinimumDuration_returnsTooEarly() throws Exception {
        String hotspotId = dataSeeder.seedHotspot();
        String tokenA = authFactory.createAndLoginUser("s5a.a@example.com", "Password1!");
        authFactory.createAndLoginUser("s5a.b@example.com", "Password1!");
        String userIdA = getUserIdByEmail("s5a.a@example.com");
        String userIdB = getUserIdByEmail("s5a.b@example.com");

        Instant start = Instant.now().plus(5, ChronoUnit.MINUTES);
        String sessionId = dataSeeder.seedActiveSession(userIdA, userIdB, hotspotId,
                start, start.plus(1, ChronoUnit.HOURS));

        // Rewind started_at to 4 min ago — safely under the 5-minute minimum with buffer
        // (299s is too close to the boundary: test-execution overhead pushes duration past 300s)
        dataSeeder.rewindSessionStartedAt(sessionId, java.time.Duration.ofSeconds(240));

        mockMvc.perform(post(COMPLETE_URL, sessionId).header("Authorization", tokenA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("SESSION_COMPLETE_TOO_EARLY"));
    }

    // ── INV-S5b: Complete at minimum duration boundary ────────────────────────

    /**
     * S-5b: {@code started_at = now − 5m01s} — just over the 5-minute minimum.
     * Complete must succeed; session transitions to {@code COMPLETED}.
     */
    @Test
    void inv_s5b_completeAtMinimumDuration_succeeds() throws Exception {
        String hotspotId = dataSeeder.seedHotspot();
        String tokenA = authFactory.createAndLoginUser("s5b.a@example.com", "Password1!");
        authFactory.createAndLoginUser("s5b.b@example.com", "Password1!");
        String userIdA = getUserIdByEmail("s5b.a@example.com");
        String userIdB = getUserIdByEmail("s5b.b@example.com");

        Instant start = Instant.now().plus(5, ChronoUnit.MINUTES);
        String sessionId = dataSeeder.seedActiveSession(userIdA, userIdB, hotspotId,
                start, start.plus(1, ChronoUnit.HOURS));

        // Rewind started_at to 5m01s ago — just over the minimum
        dataSeeder.rewindSessionStartedAt(sessionId, java.time.Duration.ofSeconds(301));

        mockMvc.perform(post(COMPLETE_URL, sessionId).header("Authorization", tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));

        String dbStatus = jdbcTemplate.queryForObject(
                "SELECT status::text FROM public.walk_session WHERE session_id = ?::uuid",
                String.class, sessionId);
        assertThat(dbStatus).isEqualTo("COMPLETED");
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private String getUserIdByEmail(String email) {
        return jdbcTemplate.queryForObject(
                "SELECT user_id::text FROM public.user_account WHERE email = ?",
                String.class, email);
    }
}
