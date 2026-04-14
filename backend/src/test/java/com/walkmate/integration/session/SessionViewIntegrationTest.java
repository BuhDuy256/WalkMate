package com.walkmate.integration.session;

import com.walkmate.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the active session list endpoint.
 *
 * <h3>Use cases covered</h3>
 * <ul>
 *   <li><b>UC-23 View Active Sessions</b> — T23-1</li>
 * </ul>
 */
class SessionViewIntegrationTest extends AbstractIntegrationTest {

    private static final String ACTIVE_SESSIONS_URL = "/api/v1/sessions/active";
    private static final ZoneId VN_ZONE             = ZoneId.of("Asia/Ho_Chi_Minh");

    private static final String TOMORROW = LocalDate.now().plusDays(1)
            .format(DateTimeFormatter.ISO_LOCAL_DATE);

    // ── T23-1: View Active Sessions ───────────────────────────────────────────

    /**
     * T23-1: User A has one PENDING and one ACTIVE session.
     * GET /api/v1/sessions/active returns exactly those two; no terminal sessions included.
     */
    @Test
    void t23_1_getActiveSessions_returnsPendingAndActiveSessions() throws Exception {
        String hotspotId = dataSeeder.seedHotspot();

        String tokenA = authFactory.createAndLoginUser("session.view.a@example.com", "Password1!");
        authFactory.createAndLoginUser("session.view.b@example.com", "Password1!");
        authFactory.createAndLoginUser("session.view.c@example.com", "Password1!");

        String userIdA = getUserIdByEmail("session.view.a@example.com");
        String userIdB = getUserIdByEmail("session.view.b@example.com");
        String userIdC = getUserIdByEmail("session.view.c@example.com");

        // Seed a PENDING session (A + B) and an ACTIVE session (A + C) — non-overlapping windows
        dataSeeder.seedPendingSession(
                userIdA, userIdB, hotspotId,
                toInstant(TOMORROW, 9.0f), toInstant(TOMORROW, 10.0f));
        dataSeeder.seedActiveSession(
                userIdA, userIdC, hotspotId,
                toInstant(TOMORROW, 11.0f), toInstant(TOMORROW, 12.0f));

        mockMvc.perform(get(ACTIVE_SESSIONS_URL)
                        .header("Authorization", tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[?(@.status == 'PENDING')]").exists())
                .andExpect(jsonPath("$.data[?(@.status == 'ACTIVE')]").exists());
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private String getUserIdByEmail(String email) {
        return jdbcTemplate.queryForObject(
                "SELECT user_id::text FROM public.user_account WHERE email = ?",
                String.class, email);
    }

    private Instant toInstant(String date, float hourFloat) {
        int totalMinutes = Math.round(hourFloat * 60);
        LocalDate localDate = LocalDate.parse(date);
        LocalTime localTime = LocalTime.of(totalMinutes / 60, totalMinutes % 60);
        return LocalDateTime.of(localDate, localTime).atZone(VN_ZONE).toInstant();
    }
}
