package com.walkmate.integration.tracking;

import com.walkmate.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the GPS route-sync endpoint.
 *
 * <h3>Use cases covered</h3>
 * <ul>
 *   <li><b>UC-28 Background GPS Route Sync</b> — T28-1 through T28-3</li>
 * </ul>
 *
 * <h3>Storage note</h3>
 * GPS data is stored in the PostgreSQL {@code session_point_chunks} table
 * (Google Encoded Polyline + packed timestamp bytes), not in MongoDB.
 */
class GpsTrackingSyncIntegrationTest extends AbstractIntegrationTest {

    private static final String SYNC_URL = "/api/v1/tracking/sync";

    // ── T28-1: Happy Path ─────────────────────────────────────────────────────

    /**
     * T28-1: User A syncs 5 GPS points for an ACTIVE session.
     * All 5 local_ids are acknowledged; one chunk row is persisted in DB.
     */
    @Test
    void t28_1_syncRoutePoints_happyPath_acknowledgesAllPoints() throws Exception {
        String hotspotId = dataSeeder.seedHotspot();
        Instant scheduledStart = Instant.now().plus(5, ChronoUnit.MINUTES);
        Instant scheduledEnd   = scheduledStart.plus(1, ChronoUnit.HOURS);

        String tokenA = authFactory.createAndLoginUser("gps.t281.a@example.com", "Password1!");
        authFactory.createAndLoginUser("gps.t281.b@example.com", "Password1!");

        String userIdA = getUserIdByEmail("gps.t281.a@example.com");
        String userIdB = getUserIdByEmail("gps.t281.b@example.com");

        String sessionId = dataSeeder.seedActiveSession(
                userIdA, userIdB, hotspotId, scheduledStart, scheduledEnd);

        // Points must have past timestamps — service rejects any timestamp > now()
        long now = Instant.now().toEpochMilli();
        String body = """
                {
                  "session_id": "%s",
                  "points": [
                    { "local_id": 1, "lat": 10.776, "lng": 106.700, "timestamp": %d, "accuracy": 5.0 },
                    { "local_id": 2, "lat": 10.777, "lng": 106.701, "timestamp": %d, "accuracy": 4.5 },
                    { "local_id": 3, "lat": 10.778, "lng": 106.702, "timestamp": %d, "accuracy": 4.8 },
                    { "local_id": 4, "lat": 10.779, "lng": 106.703, "timestamp": %d, "accuracy": 5.1 },
                    { "local_id": 5, "lat": 10.780, "lng": 106.704, "timestamp": %d, "accuracy": 4.9 }
                  ]
                }
                """.formatted(sessionId, now - 20000, now - 15000, now - 10000, now - 5000, now - 1000);

        mockMvc.perform(post(SYNC_URL)
                        .header("Authorization", tokenA)
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.acknowledged_ids").isArray())
                .andExpect(jsonPath("$.data.acknowledged_ids[0]").value(1))
                .andExpect(jsonPath("$.data.acknowledged_ids[1]").value(2))
                .andExpect(jsonPath("$.data.acknowledged_ids[2]").value(3))
                .andExpect(jsonPath("$.data.acknowledged_ids[3]").value(4))
                .andExpect(jsonPath("$.data.acknowledged_ids[4]").value(5));

        // DB: one chunk row must exist for (session, userA)
        Integer chunkCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public.session_point_chunks " +
                "WHERE session_id = ?::uuid AND user_id = ?::uuid",
                Integer.class, sessionId, userIdA);
        assertThat(chunkCount).isEqualTo(1);

        // DB: point_count on that chunk should equal 5
        Integer pointCount = jdbcTemplate.queryForObject(
                "SELECT point_count FROM public.session_point_chunks " +
                "WHERE session_id = ?::uuid AND user_id = ?::uuid",
                Integer.class, sessionId, userIdA);
        assertThat(pointCount).isEqualTo(5);
    }

    // ── T28-2: Session No Longer Active (Invariant S-6) ──────────────────────

    /**
     * T28-2: Attempting to sync GPS points against a COMPLETED session
     * returns {@code SESSION_NOT_ACTIVE} (HTTP 400).
     */
    @Test
    void t28_2_syncRoutePoints_sessionCompleted_returnsSessionNotActive() throws Exception {
        String hotspotId = dataSeeder.seedHotspot();
        Instant scheduledStart = Instant.now().plus(5, ChronoUnit.MINUTES);
        Instant scheduledEnd   = scheduledStart.plus(1, ChronoUnit.HOURS);

        String tokenA = authFactory.createAndLoginUser("gps.t282.a@example.com", "Password1!");
        authFactory.createAndLoginUser("gps.t282.b@example.com", "Password1!");

        String userIdA = getUserIdByEmail("gps.t282.a@example.com");
        String userIdB = getUserIdByEmail("gps.t282.b@example.com");

        String sessionId = dataSeeder.seedActiveSession(
                userIdA, userIdB, hotspotId, scheduledStart, scheduledEnd);

        // Force session into terminal COMPLETED state
        jdbcTemplate.update(
                "UPDATE public.walk_session SET status = 'COMPLETED'::walk_session_status " +
                "WHERE session_id = ?::uuid",
                sessionId);

        long now = Instant.now().toEpochMilli();
        String body = """
                {
                  "session_id": "%s",
                  "points": [
                    { "local_id": 1, "lat": 10.776, "lng": 106.700, "timestamp": %d, "accuracy": 5.0 }
                  ]
                }
                """.formatted(sessionId, now);

        mockMvc.perform(post(SYNC_URL)
                        .header("Authorization", tokenA)
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("SESSION_NOT_ACTIVE"));
    }

    // ── T28-3: Future Timestamp → INVALID_ARGUMENT ────────────────────────────

    /**
     * T28-3: A point whose {@code timestamp} is in the future (year 2099) passes
     * DTO Bean Validation (@Positive) but is rejected by the service-level guard,
     * which throws {@code IllegalArgumentException} → HTTP 400, {@code INVALID_ARGUMENT}.
     *
     * <p>Note: {@code lat = 999.0} would instead trigger DTO @DecimalMax and return
     * HTTP 422 VALIDATION_ERROR — that path is covered by the DTO validation contract,
     * not this test.</p>
     */
    @Test
    void t28_3_syncRoutePoints_futureTimestamp_returnsInvalidArgument() throws Exception {
        String hotspotId = dataSeeder.seedHotspot();
        Instant scheduledStart = Instant.now().plus(5, ChronoUnit.MINUTES);
        Instant scheduledEnd   = scheduledStart.plus(1, ChronoUnit.HOURS);

        String tokenA = authFactory.createAndLoginUser("gps.t283.a@example.com", "Password1!");
        authFactory.createAndLoginUser("gps.t283.b@example.com", "Password1!");

        String userIdA = getUserIdByEmail("gps.t283.a@example.com");
        String userIdB = getUserIdByEmail("gps.t283.b@example.com");

        String sessionId = dataSeeder.seedActiveSession(
                userIdA, userIdB, hotspotId, scheduledStart, scheduledEnd);

        // timestamp is 1 hour in the future — passes @Positive but fails service guard
        long futureTimestamp = Instant.now().plus(1, ChronoUnit.HOURS).toEpochMilli();
        String body = """
                {
                  "session_id": "%s",
                  "points": [
                    { "local_id": 1, "lat": 10.776, "lng": 106.700, "timestamp": %d, "accuracy": 5.0 }
                  ]
                }
                """.formatted(sessionId, futureTimestamp);

        mockMvc.perform(post(SYNC_URL)
                        .header("Authorization", tokenA)
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_ARGUMENT"));
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private String getUserIdByEmail(String email) {
        return jdbcTemplate.queryForObject(
                "SELECT user_id::text FROM public.user_account WHERE email = ?",
                String.class, email);
    }
}
