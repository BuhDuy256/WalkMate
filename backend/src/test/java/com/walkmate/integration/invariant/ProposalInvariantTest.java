package com.walkmate.integration.invariant;

import com.walkmate.application.proposal.MatchingCommandService;
import com.walkmate.support.AbstractIntegrationTest;
import com.walkmate.support.TestDataSeeder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Invariant matrix tests for proposal TTL rules.
 *
 * <h3>Invariant covered</h3>
 * <ul>
 *   <li><b>P-4</b> — Proposal TTL expiry: after {@code sweepExpiredProposals()},
 *       a PENDING public proposal becomes EXPIRED and both MATCHING intents revert
 *       to OPEN so participants re-enter the matching pool.</li>
 * </ul>
 *
 * <h3>Scope note (option 2)</h3>
 * Only the public-proposal path is tested here. The private-invite expiry path
 * ({@code MATCHING → CANCELLED}) is not implemented in {@code sweepExpiredProposals}
 * (known gap — the sweep calls {@code unlock()} unconditionally). That path is
 * indirectly exercised by T21-3 (pass private proposal).
 */
class ProposalInvariantTest extends AbstractIntegrationTest {

    @Autowired
    private MatchingCommandService matchingCommandService;

    // ── INV-P4: Public proposal TTL expiry ───────────────────────────────────

    /**
     * P-4: A PENDING public proposal whose {@code expires_at} is in the past is swept
     * to {@code EXPIRED} by the scheduler. Both MATCHING intents revert to {@code OPEN}.
     *
     * <ol>
     *   <li>Seed a PENDING public proposal (both intents MATCHING).</li>
     *   <li>Expire the proposal via JDBC ({@code expireProposal}).</li>
     *   <li>Call {@link MatchingCommandService#sweepExpiredProposals()} directly.</li>
     *   <li>Assert proposal = {@code EXPIRED}; both intents = {@code OPEN}.</li>
     * </ol>
     */
    @Test
    void inv_p4_expiredPublicProposal_intentsRevertToOpen() throws Exception {
        String hotspotId = dataSeeder.seedHotspot();
        authFactory.createAndLoginUser("p4.a@example.com", "Password1!");
        authFactory.createAndLoginUser("p4.b@example.com", "Password1!");
        String userIdA = getUserIdByEmail("p4.a@example.com");
        String userIdB = getUserIdByEmail("p4.b@example.com");

        Instant start = Instant.now().plus(1, ChronoUnit.HOURS);
        TestDataSeeder.ProposalSeed seed = dataSeeder.seedPendingProposal(
                userIdA, userIdB, hotspotId, start, start.plus(1, ChronoUnit.HOURS));

        // Set expires_at to one second in the past so the sweep picks it up
        dataSeeder.expireProposal(seed.proposalId());

        // Invoke the scheduler sweep directly — no scheduler timing dependency
        matchingCommandService.sweepExpiredProposals();

        // Proposal must now be EXPIRED
        String proposalStatus = jdbcTemplate.queryForObject(
                "SELECT status::text FROM public.match_proposal WHERE proposal_id = ?::uuid",
                String.class, seed.proposalId());
        assertThat(proposalStatus).isEqualTo("EXPIRED");

        // Both intents must revert to OPEN (public path — no private flag)
        String statusA = jdbcTemplate.queryForObject(
                "SELECT status::text FROM public.walk_intent WHERE intent_id = ?::uuid",
                String.class, seed.intentIdA());
        String statusB = jdbcTemplate.queryForObject(
                "SELECT status::text FROM public.walk_intent WHERE intent_id = ?::uuid",
                String.class, seed.intentIdB());

        assertThat(statusA).as("Intent A must revert to OPEN after TTL expiry").isEqualTo("OPEN");
        assertThat(statusB).as("Intent B must revert to OPEN after TTL expiry").isEqualTo("OPEN");
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private String getUserIdByEmail(String email) {
        return jdbcTemplate.queryForObject(
                "SELECT user_id::text FROM public.user_account WHERE email = ?",
                String.class, email);
    }
}
