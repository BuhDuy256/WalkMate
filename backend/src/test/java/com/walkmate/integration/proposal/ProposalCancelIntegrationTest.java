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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the proposal cancel (withdraw intent) endpoint.
 *
 * <h3>Use cases covered</h3>
 * <ul>
 *   <li><b>UC-22 Cancel a Proposal (Withdraw Intent)</b> — T22-1, T22-2</li>
 * </ul>
 */
class ProposalCancelIntegrationTest extends AbstractIntegrationTest {

    private static final String PROPOSALS_URL = "/api/v1/proposals";
    private static final ZoneId VN_ZONE       = ZoneId.of("Asia/Ho_Chi_Minh");

    private static final String TOMORROW = LocalDate.now().plusDays(1)
            .format(DateTimeFormatter.ISO_LOCAL_DATE);

    // ── T22-1: Cancel Proposal — Happy Path (Invariant I-6) ──────────────────

    @Test
    void t22_1_cancelProposal_callerIntentCancelled_partnerIntentReopened() throws Exception {
        String hotspotId = dataSeeder.seedHotspot();
        Instant start    = toInstant(TOMORROW, 17.0f);
        Instant end      = toInstant(TOMORROW, 18.0f);

        authFactory.createAndLoginUser("p.cancel.a@example.com", "Password1!");
        authFactory.createAndLoginUser("p.cancel.b@example.com", "Password1!");

        String userIdA = getUserIdByEmail("p.cancel.a@example.com");
        String userIdB = getUserIdByEmail("p.cancel.b@example.com");

        TestDataSeeder.ProposalSeed seed = dataSeeder.seedPendingProposal(
                userIdA, userIdB, hotspotId, start, end);

        String tokenA = loginUser("p.cancel.a");

        // User A withdraws their intent via cancel proposal
        mockMvc.perform(delete(PROPOSALS_URL + "/" + seed.proposalId())
                        .header("Authorization", tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // ── DB: User A's intent is CANCELLED (terminal — Invariant I-6) ───────
        String statusA = jdbcTemplate.queryForObject(
                "SELECT status FROM public.walk_intent WHERE intent_id = ?::uuid",
                String.class, seed.intentIdA());
        assertThat(statusA).isEqualTo("CANCELLED");

        // ── DB: User B's intent reverts to OPEN (eligible for re-matching) ────
        String statusB = jdbcTemplate.queryForObject(
                "SELECT status FROM public.walk_intent WHERE intent_id = ?::uuid",
                String.class, seed.intentIdB());
        assertThat(statusB).isEqualTo("OPEN");
    }

    // ── T22-2: Cancel Proposal — Not a Participant ────────────────────────────

    @Test
    void t22_2_cancelProposal_nonParticipant_returns400_PROPOSAL_NOT_PARTICIPANT() throws Exception {
        String hotspotId = dataSeeder.seedHotspot();
        Instant start    = toInstant(TOMORROW, 17.0f);
        Instant end      = toInstant(TOMORROW, 18.0f);

        authFactory.createAndLoginUser("p.notpart.a@example.com", "Password1!");
        authFactory.createAndLoginUser("p.notpart.b@example.com", "Password1!");
        // User C is a third party with no relation to the proposal
        String tokenC = authFactory.createAndLoginUser("p.notpart.c@example.com", "Password1!");

        String userIdA = getUserIdByEmail("p.notpart.a@example.com");
        String userIdB = getUserIdByEmail("p.notpart.b@example.com");

        TestDataSeeder.ProposalSeed seed = dataSeeder.seedPendingProposal(
                userIdA, userIdB, hotspotId, start, end);

        mockMvc.perform(delete(PROPOSALS_URL + "/" + seed.proposalId())
                        .header("Authorization", tokenC))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("PROPOSAL_NOT_PARTICIPANT"));
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private String loginUser(String emailPrefix) throws Exception {
        return authFactory.login(emailPrefix + "@example.com", "Password1!");
    }

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
