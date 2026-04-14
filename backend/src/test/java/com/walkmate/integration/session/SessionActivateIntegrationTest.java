package com.walkmate.integration.session;

import com.walkmate.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the session activation endpoint.
 *
 * <h3>Use cases covered</h3>
 * <ul>
 *   <li><b>UC-24 Activate Session (Arrive at Hotspot)</b> — T24-1 through T24-4</li>
 * </ul>
 *
 * <h3>Timing contract</h3>
 * Activation window = {@code [scheduledStart − 10 min, scheduledStart + 15 min]} (Invariant S-3).
 * Happy-path tests seed sessions with {@code scheduledStart = now + 5 min}, placing
 * current time comfortably inside the window.
 * Closed-window tests use {@code scheduledStart = now − 20 min} (window closed 5 min ago).
 */
class SessionActivateIntegrationTest extends AbstractIntegrationTest {

    private static final String ACTIVATE_URL = "/api/v1/sessions/{sessionId}/activate";

    // ── T24-1: Partial Activation (Invariant S-2) ─────────────────────────────

    /**
     * T24-1: User A arrives first. Session remains PENDING until User B also activates.
     * {@code user_a_activated_at} is set; {@code started_at} stays null.
     */
    @Test
    void t24_1_activateSession_partialActivation_statusRemainingPending() throws Exception {
        String hotspotId = dataSeeder.seedHotspot();
        Instant scheduledStart = Instant.now().plus(5, ChronoUnit.MINUTES);
        Instant scheduledEnd   = scheduledStart.plus(1, ChronoUnit.HOURS);

        String tokenA = authFactory.createAndLoginUser("session.act1.a@example.com", "Password1!");
        authFactory.createAndLoginUser("session.act1.b@example.com", "Password1!");

        String userIdA = getUserIdByEmail("session.act1.a@example.com");
        String userIdB = getUserIdByEmail("session.act1.b@example.com");

        String sessionId = dataSeeder.seedPendingSession(
                userIdA, userIdB, hotspotId, scheduledStart, scheduledEnd);

        // User A activates — partner has not yet arrived
        mockMvc.perform(post(ACTIVATE_URL, sessionId)
                        .header("Authorization", tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.user_a_activated_at").isNotEmpty())
                .andExpect(jsonPath("$.data.started_at").doesNotExist());

        // DB confirms: user_a_activated_at set, started_at still null
        String userAActivatedAt = jdbcTemplate.queryForObject(
                "SELECT user_a_activated_at::text FROM public.walk_session WHERE session_id = ?::uuid",
                String.class, sessionId);
        String startedAt = jdbcTemplate.queryForObject(
                "SELECT started_at::text FROM public.walk_session WHERE session_id = ?::uuid",
                String.class, sessionId);

        org.assertj.core.api.Assertions.assertThat(userAActivatedAt).isNotNull();
        org.assertj.core.api.Assertions.assertThat(startedAt).isNull();
    }

    // ── T24-2: Mutual Activation → ACTIVE (Invariants S-2, S-3) ─────────────

    /**
     * T24-2: Both users activate within the window.
     * Second activation returns {@code status = ACTIVE}; {@code started_at} is set in DB.
     */
    @Test
    void t24_2_activateSession_bothUsersActivate_sessionBecomesActive() throws Exception {
        String hotspotId = dataSeeder.seedHotspot();
        Instant scheduledStart = Instant.now().plus(5, ChronoUnit.MINUTES);
        Instant scheduledEnd   = scheduledStart.plus(1, ChronoUnit.HOURS);

        String tokenA = authFactory.createAndLoginUser("session.act2.a@example.com", "Password1!");
        String tokenB = authFactory.createAndLoginUser("session.act2.b@example.com", "Password1!");

        String userIdA = getUserIdByEmail("session.act2.a@example.com");
        String userIdB = getUserIdByEmail("session.act2.b@example.com");

        String sessionId = dataSeeder.seedPendingSession(
                userIdA, userIdB, hotspotId, scheduledStart, scheduledEnd);

        // User A arrives first — still PENDING
        mockMvc.perform(post(ACTIVATE_URL, sessionId)
                        .header("Authorization", tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"));

        // User B arrives — both activated → ACTIVE
        mockMvc.perform(post(ACTIVATE_URL, sessionId)
                        .header("Authorization", tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.started_at").isNotEmpty())
                .andExpect(jsonPath("$.data.user_a_activated_at").isNotEmpty())
                .andExpect(jsonPath("$.data.user_b_activated_at").isNotEmpty());

        // DB confirms started_at is set
        String startedAt = jdbcTemplate.queryForObject(
                "SELECT started_at::text FROM public.walk_session WHERE session_id = ?::uuid",
                String.class, sessionId);
        org.assertj.core.api.Assertions.assertThat(startedAt).isNotNull();
    }

    // ── T24-3: Outside Activation Window (Invariant S-3) ─────────────────────

    /**
     * T24-3: Activation window has already closed (scheduledStart was 20 minutes ago;
     * window closed 5 minutes ago). Expect {@code SESSION_ACTIVATION_WINDOW_CLOSED}.
     */
    @Test
    void t24_3_activateSession_windowClosed_returnsError() throws Exception {
        String hotspotId = dataSeeder.seedHotspot();
        // Window: [now-30min, now-5min] — current time is past the close
        Instant scheduledStart = Instant.now().minus(20, ChronoUnit.MINUTES);
        Instant scheduledEnd   = scheduledStart.plus(1, ChronoUnit.HOURS);

        String tokenA = authFactory.createAndLoginUser("session.act3.a@example.com", "Password1!");
        authFactory.createAndLoginUser("session.act3.b@example.com", "Password1!");

        String userIdA = getUserIdByEmail("session.act3.a@example.com");
        String userIdB = getUserIdByEmail("session.act3.b@example.com");

        String sessionId = dataSeeder.seedPendingSession(
                userIdA, userIdB, hotspotId, scheduledStart, scheduledEnd);

        mockMvc.perform(post(ACTIVATE_URL, sessionId)
                        .header("Authorization", tokenA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("SESSION_ACTIVATION_WINDOW_CLOSED"));
    }

    // ── T24-4: Session Not PENDING ────────────────────────────────────────────

    /**
     * T24-4: Calling activate on an already-ACTIVE session returns
     * {@code SESSION_NOT_PENDING}.
     */
    @Test
    void t24_4_activateSession_sessionNotPending_returnsError() throws Exception {
        String hotspotId = dataSeeder.seedHotspot();
        Instant scheduledStart = Instant.now().plus(5, ChronoUnit.MINUTES);
        Instant scheduledEnd   = scheduledStart.plus(1, ChronoUnit.HOURS);

        String tokenA = authFactory.createAndLoginUser("session.act4.a@example.com", "Password1!");
        authFactory.createAndLoginUser("session.act4.b@example.com", "Password1!");

        String userIdA = getUserIdByEmail("session.act4.a@example.com");
        String userIdB = getUserIdByEmail("session.act4.b@example.com");

        // Session is already ACTIVE — activation is not allowed
        String sessionId = dataSeeder.seedActiveSession(
                userIdA, userIdB, hotspotId, scheduledStart, scheduledEnd);

        mockMvc.perform(post(ACTIVATE_URL, sessionId)
                        .header("Authorization", tokenA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("SESSION_NOT_PENDING"));
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private String getUserIdByEmail(String email) {
        return jdbcTemplate.queryForObject(
                "SELECT user_id::text FROM public.user_account WHERE email = ?",
                String.class, email);
    }
}
