package com.walkmate.integration.report;

import com.walkmate.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the incident report submission endpoint.
 *
 * <h3>Use cases covered</h3>
 * <ul>
 *   <li><b>UC-32 Submit an Incident Report</b> — T32-1 through T32-4</li>
 * </ul>
 *
 * <h3>Reporting window contract</h3>
 * <ul>
 *   <li>COMPLETED: 72-hour window from {@code ended_at}</li>
 *   <li>ABORTED / NO_SHOW: 24-hour window from {@code ended_at}</li>
 *   <li>ACTIVE: always allowed (no window)</li>
 *   <li>PENDING / CANCELLED: never allowed → {@code REPORT_SESSION_INVALID_STATUS}</li>
 * </ul>
 */
class ReportIntegrationTest extends AbstractIntegrationTest {

    private static final String REPORT_URL = "/api/v1/sessions/{sessionId}/report";

    // ── T32-1: Happy Path ─────────────────────────────────────────────────────

    /**
     * T32-1: User A reports User B on a COMPLETED session.
     * Returns {@code 201 Created} with a non-null {@code reportId}.
     */
    @Test
    void t32_1_submitReport_happyPath_reportCreated() throws Exception {
        String hotspotId = dataSeeder.seedHotspot();
        Instant start = Instant.now().plus(5, ChronoUnit.MINUTES);
        Instant end   = start.plus(1, ChronoUnit.HOURS);

        String tokenA = authFactory.createAndLoginUser("report.t321.a@example.com", "Password1!");
        authFactory.createAndLoginUser("report.t321.b@example.com", "Password1!");

        String userIdA = getUserIdByEmail("report.t321.a@example.com");
        String userIdB = getUserIdByEmail("report.t321.b@example.com");

        String sessionId = seedCompletedSession(userIdA, userIdB, hotspotId, start, end);

        String body = """
                {
                  "reportedUserId": "%s",
                  "reason": "Partner was aggressive and threatening."
                }
                """.formatted(userIdB);

        mockMvc.perform(post(REPORT_URL, sessionId)
                        .header("Authorization", tokenA)
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.reportId").isNotEmpty());
    }

    // ── T32-2: Reporting Window Expired ───────────────────────────────────────

    /**
     * T32-2: COMPLETED session with {@code ended_at} set to 73 hours ago.
     * The 72-hour reporting window has closed → {@code REPORT_WINDOW_EXPIRED} (HTTP 400).
     */
    @Test
    void t32_2_submitReport_windowExpired_returnsReportWindowExpired() throws Exception {
        String hotspotId = dataSeeder.seedHotspot();
        Instant start = Instant.now().plus(5, ChronoUnit.MINUTES);
        Instant end   = start.plus(1, ChronoUnit.HOURS);

        String tokenA = authFactory.createAndLoginUser("report.t322.a@example.com", "Password1!");
        authFactory.createAndLoginUser("report.t322.b@example.com", "Password1!");

        String userIdA = getUserIdByEmail("report.t322.a@example.com");
        String userIdB = getUserIdByEmail("report.t322.b@example.com");

        String sessionId = dataSeeder.seedActiveSession(userIdA, userIdB, hotspotId, start, end);

        // ended_at is 73 hours ago — outside the 72-hour COMPLETED reporting window
        jdbcTemplate.update(
                "UPDATE public.walk_session " +
                "SET status = 'COMPLETED'::walk_session_status, " +
                "    ended_at = now() - interval '73 hours', " +
                "    total_distance_km = 1.0, " +
                "    total_duration_seconds = 600 " +
                "WHERE session_id = ?::uuid",
                sessionId);

        String body = """
                {
                  "reportedUserId": "%s",
                  "reason": "Harassment during the walk."
                }
                """.formatted(userIdB);

        mockMvc.perform(post(REPORT_URL, sessionId)
                        .header("Authorization", tokenA)
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("REPORT_WINDOW_EXPIRED"));
    }

    // ── T32-3: Duplicate Report ───────────────────────────────────────────────

    /**
     * T32-3: Submitting a second report for the same session returns
     * {@code REPORT_ALREADY_SUBMITTED} (HTTP 400).
     */
    @Test
    void t32_3_submitReport_duplicate_returnsReportAlreadySubmitted() throws Exception {
        String hotspotId = dataSeeder.seedHotspot();
        Instant start = Instant.now().plus(5, ChronoUnit.MINUTES);
        Instant end   = start.plus(1, ChronoUnit.HOURS);

        String tokenA = authFactory.createAndLoginUser("report.t323.a@example.com", "Password1!");
        authFactory.createAndLoginUser("report.t323.b@example.com", "Password1!");

        String userIdA = getUserIdByEmail("report.t323.a@example.com");
        String userIdB = getUserIdByEmail("report.t323.b@example.com");

        String sessionId = seedCompletedSession(userIdA, userIdB, hotspotId, start, end);

        String body = """
                {
                  "reportedUserId": "%s",
                  "reason": "Inappropriate behaviour."
                }
                """.formatted(userIdB);

        // First report — succeeds
        mockMvc.perform(post(REPORT_URL, sessionId)
                        .header("Authorization", tokenA)
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        // Second report — rejected
        mockMvc.perform(post(REPORT_URL, sessionId)
                        .header("Authorization", tokenA)
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("REPORT_ALREADY_SUBMITTED"));
    }

    // ── T32-4: Non-Reportable Session Status ──────────────────────────────────

    /**
     * T32-4: Reporting against a PENDING session (pre-walk) returns
     * {@code REPORT_SESSION_INVALID_STATUS} (HTTP 400).
     */
    @Test
    void t32_4_submitReport_pendingSession_returnsReportSessionInvalidStatus() throws Exception {
        String hotspotId = dataSeeder.seedHotspot();
        Instant start = Instant.now().plus(5, ChronoUnit.MINUTES);
        Instant end   = start.plus(1, ChronoUnit.HOURS);

        String tokenA = authFactory.createAndLoginUser("report.t324.a@example.com", "Password1!");
        authFactory.createAndLoginUser("report.t324.b@example.com", "Password1!");

        String userIdA = getUserIdByEmail("report.t324.a@example.com");
        String userIdB = getUserIdByEmail("report.t324.b@example.com");

        // Session stays PENDING — not yet activated
        String sessionId = dataSeeder.seedPendingSession(userIdA, userIdB, hotspotId, start, end);

        String body = """
                {
                  "reportedUserId": "%s",
                  "reason": "Pre-emptive report attempt."
                }
                """.formatted(userIdB);

        mockMvc.perform(post(REPORT_URL, sessionId)
                        .header("Authorization", tokenA)
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("REPORT_SESSION_INVALID_STATUS"));
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
