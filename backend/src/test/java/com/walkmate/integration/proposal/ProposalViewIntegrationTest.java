package com.walkmate.integration.proposal;

import com.walkmate.support.AbstractIntegrationTest;
import com.walkmate.support.TestDataSeeder;
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
 * Integration tests for the proposal list endpoint.
 *
 * <h3>Use cases covered</h3>
 * <ul>
 *   <li><b>UC-19 View Incoming Proposals</b> — T19-1 (authenticated user sees their PENDING proposals)</li>
 * </ul>
 */
class ProposalViewIntegrationTest extends AbstractIntegrationTest {

    private static final String PROPOSALS_URL = "/api/v1/proposals";
    private static final ZoneId VN_ZONE       = ZoneId.of("Asia/Ho_Chi_Minh");

    private static final String TOMORROW = LocalDate.now().plusDays(1)
            .format(DateTimeFormatter.ISO_LOCAL_DATE);

    // ── T19-1: View Incoming Proposals ────────────────────────────────────────

    @Test
    void t19_1_getProposals_authenticated_returnsPendingProposals() throws Exception {
        String hotspotId = dataSeeder.seedHotspot();
        Instant start    = toInstant(TOMORROW, 17.0f);
        Instant end      = toInstant(TOMORROW, 18.0f);

        String tokenA = authFactory.createAndLoginUser("proposal.view.a@example.com", "Password1!");
        authFactory.createAndLoginUser("proposal.view.b@example.com", "Password1!");

        String userIdA = getUserIdByEmail("proposal.view.a@example.com");
        String userIdB = getUserIdByEmail("proposal.view.b@example.com");

        TestDataSeeder.ProposalSeed seed = dataSeeder.seedPendingProposal(
                userIdA, userIdB, hotspotId, start, end);

        mockMvc.perform(get(PROPOSALS_URL)
                        .header("Authorization", tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].proposal_id").value(seed.proposalId()))
                .andExpect(jsonPath("$.data[0].expires_at").isNotEmpty())
                .andExpect(jsonPath("$.data[0].status").value("PENDING"));
    }

    // ── Private helpers ────────────────────────────────────────────────────────

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
