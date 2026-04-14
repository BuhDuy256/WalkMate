package com.walkmate.integration.session;

import com.walkmate.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for session history and route-replay endpoints.
 *
 * <h3>Use cases covered</h3>
 * <ul>
 *   <li><b>UC-29 View Session History</b> — T29-1</li>
 *   <li><b>UC-30 View Session Route Replay</b> — T30-1, T30-2, T30-3</li>
 * </ul>
 */
class SessionHistoryIntegrationTest extends AbstractIntegrationTest {

    private static final String HISTORY_URL  = "/api/v1/sessions/history";
    private static final String ROUTE_URL    = "/api/v1/sessions/{sessionId}/route";
    private static final String SYNC_URL     = "/api/v1/tracking/sync";

    // ── T29-1: View Session History ───────────────────────────────────────────

    /**
     * T29-1: History list contains both a COMPLETED and an ABORTED session.
     * COMPLETED session carries real distance/duration stats;
     * ABORTED session defaults to 0.0 / 0.
     */
    @Test
    void t29_1_sessionHistory_completedAndAborted_bothAppearWithCorrectStats() throws Exception {
        String hotspotId = dataSeeder.seedHotspot();
        Instant start = Instant.now().plus(5, ChronoUnit.MINUTES);
        Instant end   = start.plus(1, ChronoUnit.HOURS);

        String tokenA = authFactory.createAndLoginUser("hist.t291.a@example.com", "Password1!");
        authFactory.createAndLoginUser("hist.t291.b@example.com", "Password1!");
        authFactory.createAndLoginUser("hist.t291.c@example.com", "Password1!");

        String userIdA = getUserIdByEmail("hist.t291.a@example.com");
        String userIdB = getUserIdByEmail("hist.t291.b@example.com");
        String userIdC = getUserIdByEmail("hist.t291.c@example.com");

        // Session 1: COMPLETED with stats
        String completedSessionId = dataSeeder.seedActiveSession(userIdA, userIdB, hotspotId, start, end);
        jdbcTemplate.update(
                "UPDATE public.walk_session " +
                "SET status = 'COMPLETED'::walk_session_status, " +
                "    ended_at = now(), " +
                "    total_distance_km = 2.5, " +
                "    total_duration_seconds = 1800 " +
                "WHERE session_id = ?::uuid",
                completedSessionId);

        // Session 2: ABORTED (no stats)
        String abortedSessionId = dataSeeder.seedActiveSession(
                userIdA, userIdC, hotspotId,
                start.plus(2, ChronoUnit.HOURS),
                end.plus(2, ChronoUnit.HOURS));
        jdbcTemplate.update(
                "UPDATE public.walk_session " +
                "SET status = 'ABORTED'::walk_session_status, ended_at = now() " +
                "WHERE session_id = ?::uuid",
                abortedSessionId);

        mockMvc.perform(get(HISTORY_URL).header("Authorization", tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                // At least 2 sessions returned
                .andExpect(jsonPath("$.data.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)));

        // Assert COMPLETED session has real stats
        String completedStats = mockMvc.perform(get(HISTORY_URL).header("Authorization", tokenA))
                .andReturn().getResponse().getContentAsString();

        com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(completedStats);
        com.fasterxml.jackson.databind.JsonNode sessions = root.get("data");

        boolean foundCompleted = false;
        boolean foundAborted   = false;
        for (com.fasterxml.jackson.databind.JsonNode session : sessions) {
            String status = session.get("status").asText();
            if ("COMPLETED".equals(status) && session.get("session_id").asText().equals(completedSessionId)) {
                assertThat(session.get("total_distance_km").asDouble()).isGreaterThan(0.0);
                assertThat(session.get("duration_minutes").asInt()).isGreaterThan(0);
                foundCompleted = true;
            }
            if ("ABORTED".equals(status) && session.get("session_id").asText().equals(abortedSessionId)) {
                assertThat(session.get("total_distance_km").asDouble()).isEqualTo(0.0);
                assertThat(session.get("duration_minutes").asInt()).isEqualTo(0);
                foundAborted = true;
            }
        }
        assertThat(foundCompleted).as("COMPLETED session must be in history").isTrue();
        assertThat(foundAborted).as("ABORTED session must be in history").isTrue();
    }

    // ── T30-1: Route Replay — Happy Path ──────────────────────────────────────

    /**
     * T30-1: User A syncs GPS points during an ACTIVE session; session is then
     * completed. Route replay returns a non-empty {@code user_a_polylines} list.
     */
    @Test
    void t30_1_routeReplay_withGpsData_returnsPolylines() throws Exception {
        String hotspotId = dataSeeder.seedHotspot();
        Instant start = Instant.now().plus(5, ChronoUnit.MINUTES);
        Instant end   = start.plus(1, ChronoUnit.HOURS);

        String tokenA = authFactory.createAndLoginUser("route.t301.a@example.com", "Password1!");
        authFactory.createAndLoginUser("route.t301.b@example.com", "Password1!");

        String userIdA = getUserIdByEmail("route.t301.a@example.com");
        String userIdB = getUserIdByEmail("route.t301.b@example.com");

        String sessionId = dataSeeder.seedActiveSession(userIdA, userIdB, hotspotId, start, end);

        // Sync 3 GPS points as User A (past timestamps)
        long now = Instant.now().toEpochMilli();
        String syncBody = """
                {
                  "session_id": "%s",
                  "points": [
                    { "local_id": 1, "lat": 10.776, "lng": 106.700, "timestamp": %d, "accuracy": 5.0 },
                    { "local_id": 2, "lat": 10.777, "lng": 106.701, "timestamp": %d, "accuracy": 4.5 },
                    { "local_id": 3, "lat": 10.778, "lng": 106.702, "timestamp": %d, "accuracy": 4.8 }
                  ]
                }
                """.formatted(sessionId, now - 15000, now - 10000, now - 5000);

        mockMvc.perform(post(SYNC_URL)
                        .header("Authorization", tokenA)
                        .contentType(APPLICATION_JSON)
                        .content(syncBody))
                .andExpect(status().isOk());

        // Force session to COMPLETED
        jdbcTemplate.update(
                "UPDATE public.walk_session " +
                "SET status = 'COMPLETED'::walk_session_status, " +
                "    ended_at = now(), " +
                "    total_distance_km = 1.2, " +
                "    total_duration_seconds = 900 " +
                "WHERE session_id = ?::uuid",
                sessionId);

        mockMvc.perform(get(ROUTE_URL, sessionId).header("Authorization", tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.session_id").value(sessionId))
                .andExpect(jsonPath("$.data.user_a_polylines").isArray())
                .andExpect(jsonPath("$.data.user_a_polylines.length()").value(
                        org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.user_b_polylines").isArray());
    }

    // ── T30-2: Route Replay — Session Not Finished ────────────────────────────

    /**
     * T30-2: Calling route replay on an ACTIVE session returns
     * {@code SESSION_NOT_FINISHED} (HTTP 400).
     */
    @Test
    void t30_2_routeReplay_sessionActive_returnsSessionNotFinished() throws Exception {
        String hotspotId = dataSeeder.seedHotspot();
        Instant start = Instant.now().plus(5, ChronoUnit.MINUTES);
        Instant end   = start.plus(1, ChronoUnit.HOURS);

        String tokenA = authFactory.createAndLoginUser("route.t302.a@example.com", "Password1!");
        authFactory.createAndLoginUser("route.t302.b@example.com", "Password1!");

        String userIdA = getUserIdByEmail("route.t302.a@example.com");
        String userIdB = getUserIdByEmail("route.t302.b@example.com");

        String sessionId = dataSeeder.seedActiveSession(userIdA, userIdB, hotspotId, start, end);

        mockMvc.perform(get(ROUTE_URL, sessionId).header("Authorization", tokenA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("SESSION_NOT_FINISHED"));
    }

    // ── T30-3: Route Replay — No GPS Data (Cancelled Early) ──────────────────

    /**
     * T30-3: A CANCELLED session with no GPS points returns {@code 200 OK}
     * with empty polyline arrays — not an error.
     *
     * <p><b>Note:</b> UC-30 spec states only COMPLETED sessions support route
     * replay. If the implementation also rejects CANCELLED with SESSION_NOT_FINISHED,
     * this test will catch that discrepancy and should be updated accordingly.</p>
     */
    @Test
    void t30_3_routeReplay_cancelledSessionNoGps_returnsEmptyPolylines() throws Exception {
        String hotspotId = dataSeeder.seedHotspot();
        Instant start = Instant.now().plus(5, ChronoUnit.MINUTES);
        Instant end   = start.plus(1, ChronoUnit.HOURS);

        String tokenA = authFactory.createAndLoginUser("route.t303.a@example.com", "Password1!");
        authFactory.createAndLoginUser("route.t303.b@example.com", "Password1!");

        String userIdA = getUserIdByEmail("route.t303.a@example.com");
        String userIdB = getUserIdByEmail("route.t303.b@example.com");

        String sessionId = dataSeeder.seedActiveSession(userIdA, userIdB, hotspotId, start, end);

        jdbcTemplate.update(
                "UPDATE public.walk_session " +
                "SET status = 'CANCELLED'::walk_session_status, ended_at = now() " +
                "WHERE session_id = ?::uuid",
                sessionId);

        mockMvc.perform(get(ROUTE_URL, sessionId).header("Authorization", tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.user_a_polylines").isArray())
                .andExpect(jsonPath("$.data.user_a_polylines.length()").value(0))
                .andExpect(jsonPath("$.data.user_b_polylines").isArray())
                .andExpect(jsonPath("$.data.user_b_polylines.length()").value(0));
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private String getUserIdByEmail(String email) {
        return jdbcTemplate.queryForObject(
                "SELECT user_id::text FROM public.user_account WHERE email = ?",
                String.class, email);
    }
}
