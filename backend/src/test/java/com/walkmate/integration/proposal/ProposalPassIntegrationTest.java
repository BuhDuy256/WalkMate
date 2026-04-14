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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the proposal pass (reject) endpoint.
 *
 * <h3>Use cases covered</h3>
 * <ul>
 *   <li><b>UC-21 Pass (Reject) a Proposal</b> — T21-1, T21-2, T21-3</li>
 * </ul>
 *
 * <h3>Private-invite behaviour (T21-3)</h3>
 * <p>When a private-invite proposal is passed, the production code was fixed in
 * {@link com.walkmate.application.proposal.MatchingCommandService#passProposal}
 * to cancel both intents (MATCHING → CANCELLED) instead of unlocking them
 * to OPEN, satisfying Invariant I-7 and UC-21 private-path spec.
 */
class ProposalPassIntegrationTest extends AbstractIntegrationTest {

    private static final String PROPOSALS_URL = "/api/v1/proposals";
    private static final ZoneId VN_ZONE       = ZoneId.of("Asia/Ho_Chi_Minh");

    private static final String TOMORROW = LocalDate.now().plusDays(1)
            .format(DateTimeFormatter.ISO_LOCAL_DATE);

    // ── T21-1: Pass Public Proposal — Happy Path (Invariant X-3) ─────────────

    @Test
    void t21_1_passProposal_publicProposal_returns200_intentsReverToOpen_excludeListUpdated()
            throws Exception {
        TestDataSeeder.ProposalSeed seed = seedPublicProposalForTomorrow("p.pass.a", "p.pass.b");
        String tokenA = loginUser("p.pass.a");

        mockMvc.perform(post(PROPOSALS_URL + "/" + seed.proposalId() + "/pass")
                        .header("Authorization", tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // ── DB: proposal moves to REJECTED ────────────────────────────────────
        String proposalStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM public.match_proposal WHERE proposal_id = ?::uuid",
                String.class, seed.proposalId());
        assertThat(proposalStatus).isEqualTo("REJECTED");

        // ── DB: both intents revert to OPEN (public path) ─────────────────────
        String statusA = jdbcTemplate.queryForObject(
                "SELECT status FROM public.walk_intent WHERE intent_id = ?::uuid",
                String.class, seed.intentIdA());
        String statusB = jdbcTemplate.queryForObject(
                "SELECT status FROM public.walk_intent WHERE intent_id = ?::uuid",
                String.class, seed.intentIdB());
        assertThat(statusA).isEqualTo("OPEN");
        assertThat(statusB).isEqualTo("OPEN");

        // ── DB: exclude list updated on User A's intent (Invariant X-3) ───────
        // User A is callerIntentId=intentIdA, partnerUserId=userIdB → excludedUserIds[userIdB]
        Boolean partnerExcluded = jdbcTemplate.queryForObject(
                """
                SELECT ?::uuid = ANY(excluded_user_ids)
                FROM public.walk_intent WHERE intent_id = ?::uuid
                """,
                Boolean.class, seed.userIdB(), seed.intentIdA());
        assertThat(partnerExcluded).isTrue();
    }

    // ── T21-2: Pass Proposal — Already Terminal ────────────────────────────────

    @Test
    void t21_2_passProposal_alreadyRejected_returns400_PROPOSAL_ALREADY_TERMINAL() throws Exception {
        TestDataSeeder.ProposalSeed seed = seedPublicProposalForTomorrow("p.term.a", "p.term.b");

        dataSeeder.forceProposalStatus(seed.proposalId(), "REJECTED");

        mockMvc.perform(post(PROPOSALS_URL + "/" + seed.proposalId() + "/pass")
                        .header("Authorization", loginUser("p.term.a")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("PROPOSAL_ALREADY_TERMINAL"));
    }

    // ── T21-3: Pass Private Invite — Intents Cancelled (Invariant I-7) ────────

    @Test
    void t21_3_passProposal_privateInvite_returns200_intentsAreCancelled_notPublicised()
            throws Exception {
        String hotspotId = dataSeeder.seedHotspot();
        Instant start    = toInstant(TOMORROW, 17.0f);
        Instant end      = toInstant(TOMORROW, 18.0f);

        authFactory.createAndLoginUser("p.priv.a@example.com", "Password1!");
        authFactory.createAndLoginUser("p.priv.b@example.com", "Password1!");

        String userIdA = getUserIdByEmail("p.priv.a@example.com");
        String userIdB = getUserIdByEmail("p.priv.b@example.com");

        // Seed a private-invite proposal (is_private = true on both intents)
        TestDataSeeder.ProposalSeed seed = dataSeeder.seedPendingPrivateProposal(
                userIdA, userIdB, hotspotId, start, end);

        // User B passes the private invite
        mockMvc.perform(post(PROPOSALS_URL + "/" + seed.proposalId() + "/pass")
                        .header("Authorization", loginUser("p.priv.b")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // ── DB: proposal is REJECTED ───────────────────────────────────────────
        String proposalStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM public.match_proposal WHERE proposal_id = ?::uuid",
                String.class, seed.proposalId());
        assertThat(proposalStatus).isEqualTo("REJECTED");

        // ── DB: BOTH private intents are CANCELLED — not reopened to OPEN (I-7) ─
        String statusA = jdbcTemplate.queryForObject(
                "SELECT status FROM public.walk_intent WHERE intent_id = ?::uuid",
                String.class, seed.intentIdA());
        String statusB = jdbcTemplate.queryForObject(
                "SELECT status FROM public.walk_intent WHERE intent_id = ?::uuid",
                String.class, seed.intentIdB());
        assertThat(statusA).isEqualTo("CANCELLED");
        assertThat(statusB).isEqualTo("CANCELLED");

        // ── DB: no new public OPEN intent exists for User B ───────────────────
        Integer openPublicCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM public.walk_intent
                WHERE user_id = ?::uuid
                  AND status = 'OPEN'::intent_status
                  AND is_private = false
                """,
                Integer.class, userIdB);
        assertThat(openPublicCount).isZero();
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private TestDataSeeder.ProposalSeed seedPublicProposalForTomorrow(String emailPrefixA,
                                                                       String emailPrefixB)
            throws Exception {
        String hotspotId = dataSeeder.seedHotspot();
        Instant start    = toInstant(TOMORROW, 17.0f);
        Instant end      = toInstant(TOMORROW, 18.0f);

        authFactory.createAndLoginUser(emailPrefixA + "@example.com", "Password1!");
        authFactory.createAndLoginUser(emailPrefixB + "@example.com", "Password1!");

        String userIdA = getUserIdByEmail(emailPrefixA + "@example.com");
        String userIdB = getUserIdByEmail(emailPrefixB + "@example.com");

        return dataSeeder.seedPendingProposal(userIdA, userIdB, hotspotId, start, end);
    }

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
