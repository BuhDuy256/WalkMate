package com.walkmate.integration.proposal;

import com.walkmate.support.AbstractIntegrationTest;
import com.walkmate.support.TestDataSeeder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the proposal accept endpoint.
 *
 * <h3>Use cases covered</h3>
 * <ul>
 *   <li><b>UC-20 Accept a Proposal</b> — T20-1 through T20-5</li>
 * </ul>
 *
 * <h3>Concurrency test (T20-5) strategy</h3>
 * <p>T20-5 simulates a User B double-tap: after User A has accepted (proposal is PENDING),
 * two threads simultaneously call User B's accept. PostgreSQL's OCC guard
 * ({@code version} column in {@code match_proposal}) ensures exactly one thread
 * creates the session; the other receives {@code PROPOSAL_CONCURRENT_MODIFICATION}
 * or {@code PROPOSAL_ALREADY_TERMINAL}.
 * MockMvc is thread-safe — each {@code perform()} creates an independent request context
 * with its own transaction.
 */
class ProposalAcceptIntegrationTest extends AbstractIntegrationTest {

    private static final String PROPOSALS_URL = "/api/v1/proposals";
    private static final ZoneId VN_ZONE       = ZoneId.of("Asia/Ho_Chi_Minh");

    private static final String TOMORROW = LocalDate.now().plusDays(1)
            .format(DateTimeFormatter.ISO_LOCAL_DATE);

    /** Injected to assert MongoDB chat-room creation in T20-2. */
    @Autowired
    private MongoTemplate mongoTemplate;

    // ── T20-1: Partial Acceptance — Only One User Accepts ─────────────────────

    @Test
    void t20_1_acceptProposal_partialAccept_returnsPending_noSessionCreated() throws Exception {
        TestDataSeeder.ProposalSeed seed = seedProposalForTomorrow("p.partial.a", "p.partial.b");

        // User A accepts — partner has not yet accepted
        mockMvc.perform(post(PROPOSALS_URL + "/" + seed.proposalId() + "/accept")
                        .header("Authorization", loginUser("p.partial.a")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.session_id").isEmpty());

        // No WalkSession row must have been created
        Integer sessionCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public.walk_session WHERE proposal_id = ?::uuid",
                Integer.class, seed.proposalId());
        assertThat(sessionCount).isZero();
    }

    // ── T20-2: Both Accept → Session Created (P-2, P-3, I-3) ─────────────────

    @Test
    void t20_2_acceptProposal_bothAccept_returnsConfirmed_sessionCreated_intentsConsumed_chatRoomExists()
            throws Exception {
        TestDataSeeder.ProposalSeed seed = seedProposalForTomorrow("p.both.a", "p.both.b");
        String tokenA = loginUser("p.both.a");
        String tokenB = loginUser("p.both.b");

        // User A accepts first → PENDING
        mockMvc.perform(post(PROPOSALS_URL + "/" + seed.proposalId() + "/accept")
                        .header("Authorization", tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"));

        // User B accepts → both accepted → CONFIRMED, session_id populated
        MvcResult confirmResult = mockMvc.perform(
                        post(PROPOSALS_URL + "/" + seed.proposalId() + "/accept")
                                .header("Authorization", tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.session_id").isNotEmpty())
                .andReturn();

        String sessionId = objectMapper
                .readTree(confirmResult.getResponse().getContentAsString())
                .at("/data/session_id").asText();

        // ── DB: WalkSession in PENDING status ──────────────────────────────────
        String sessionStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM public.walk_session WHERE session_id = ?::uuid",
                String.class, sessionId);
        assertThat(sessionStatus).isEqualTo("PENDING");

        // ── DB: both intents CONSUMED (Invariant I-3) ─────────────────────────
        String statusA = jdbcTemplate.queryForObject(
                "SELECT status FROM public.walk_intent WHERE intent_id = ?::uuid",
                String.class, seed.intentIdA());
        String statusB = jdbcTemplate.queryForObject(
                "SELECT status FROM public.walk_intent WHERE intent_id = ?::uuid",
                String.class, seed.intentIdB());
        assertThat(statusA).isEqualTo("CONSUMED");
        assertThat(statusB).isEqualTo("CONSUMED");

        // ── MongoDB: chat room initialised (P-3 step 3) ───────────────────────
        boolean chatRoomExists = mongoTemplate.exists(
                Query.query(Criteria.where("_id").is(sessionId)),
                "chat_rooms");
        assertThat(chatRoomExists).isTrue();
    }

    // ── T20-3: Proposal Already Terminal (Invariant I-6) ──────────────────────

    @Test
    void t20_3_acceptProposal_proposalExpired_returns400_PROPOSAL_ALREADY_TERMINAL() throws Exception {
        TestDataSeeder.ProposalSeed seed = seedProposalForTomorrow("p.expired.a", "p.expired.b");

        // Force the proposal to EXPIRED status (simulates TTL expiry)
        dataSeeder.forceProposalStatus(seed.proposalId(), "EXPIRED");

        mockMvc.perform(post(PROPOSALS_URL + "/" + seed.proposalId() + "/accept")
                        .header("Authorization", loginUser("p.expired.a")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("PROPOSAL_ALREADY_TERMINAL"));
    }

    // ── T20-4: Intent No Longer MATCHING ─────────────────────────────────────

    @Test
    void t20_4_acceptProposal_intentNoLongerMatching_returns400_PROPOSAL_INTENT_NO_LONGER_OPEN()
            throws Exception {
        TestDataSeeder.ProposalSeed seed = seedProposalForTomorrow("p.stale.a", "p.stale.b");
        String tokenA = loginUser("p.stale.a");
        String tokenB = loginUser("p.stale.b");

        // User A accepts first (partial) — proposal version increments to 1
        mockMvc.perform(post(PROPOSALS_URL + "/" + seed.proposalId() + "/accept")
                        .header("Authorization", tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"));

        // Simulate concurrent cancellation of User B's intent (e.g. expired or withdrawn)
        dataSeeder.forceIntentStatus(seed.intentIdB(), "CANCELLED");

        // User B now accepts → both recorded → critical section → intent check fails
        mockMvc.perform(post(PROPOSALS_URL + "/" + seed.proposalId() + "/accept")
                        .header("Authorization", tokenB))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("PROPOSAL_INTENT_NO_LONGER_OPEN"));

        // No WalkSession must have been created (transaction rolled back)
        Integer sessionCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public.walk_session WHERE proposal_id = ?::uuid",
                Integer.class, seed.proposalId());
        assertThat(sessionCount).isZero();
    }

    // ── T20-5: Concurrent Modification — No Duplicate Session (Invariant X-5) ─

    @Test
    void t20_5_acceptProposal_concurrentDoubleAccept_exactlyOneSessionCreated() throws Exception {
        TestDataSeeder.ProposalSeed seed = seedProposalForTomorrow("p.concurrent.a", "p.concurrent.b");
        String tokenA = loginUser("p.concurrent.a");
        String tokenB = loginUser("p.concurrent.b");

        // User A accepts (sequential) — leaves proposal PENDING with acceptedByA=true
        mockMvc.perform(post(PROPOSALS_URL + "/" + seed.proposalId() + "/accept")
                        .header("Authorization", tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"));

        // Two threads simultaneously call User B's accept (double-tap simulation).
        // One will create the session (CONFIRMED); the other will hit the OCC guard
        // (PROPOSAL_CONCURRENT_MODIFICATION) or find the proposal already CONFIRMED
        // (PROPOSAL_ALREADY_TERMINAL). Either is acceptable — the invariant is atomicity.
        CountDownLatch startGate = new CountDownLatch(1);
        List<Integer> httpStatuses = new CopyOnWriteArrayList<>();

        Runnable acceptTask = () -> {
            try {
                startGate.await();
                MvcResult result = mockMvc.perform(
                                post(PROPOSALS_URL + "/" + seed.proposalId() + "/accept")
                                        .header("Authorization", tokenB))
                        .andReturn();
                httpStatuses.add(result.getResponse().getStatus());
            } catch (Exception e) {
                httpStatuses.add(500);
            }
        };

        Thread t1 = new Thread(acceptTask);
        Thread t2 = new Thread(acceptTask);
        t1.start();
        t2.start();
        startGate.countDown();   // release both threads at the same moment
        t1.join(5_000);
        t2.join(5_000);

        // Invariant X-5: exactly one WalkSession must exist — no duplicate
        Integer sessionCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public.walk_session WHERE proposal_id = ?::uuid",
                Integer.class, seed.proposalId());
        assertThat(sessionCount).isEqualTo(1);

        // At least one thread must have succeeded (200 OK)
        long successCount = httpStatuses.stream().filter(s -> s == 200).count();
        assertThat(successCount).isGreaterThanOrEqualTo(1);
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    /**
     * Registers two users (suffix-keyed emails), seeds a PENDING proposal, and
     * returns the seed record. Callers must call {@link #loginUser(String)} separately
     * to obtain fresh tokens.
     */
    private TestDataSeeder.ProposalSeed seedProposalForTomorrow(String emailPrefixA,
                                                                 String emailPrefixB) throws Exception {
        String hotspotId = dataSeeder.seedHotspot();
        Instant start    = toInstant(TOMORROW, 17.0f);
        Instant end      = toInstant(TOMORROW, 18.0f);

        authFactory.createAndLoginUser(emailPrefixA + "@example.com", "Password1!");
        authFactory.createAndLoginUser(emailPrefixB + "@example.com", "Password1!");

        String userIdA = getUserIdByEmail(emailPrefixA + "@example.com");
        String userIdB = getUserIdByEmail(emailPrefixB + "@example.com");

        return dataSeeder.seedPendingProposal(userIdA, userIdB, hotspotId, start, end);
    }

    /** Returns a fresh Bearer token for an already-registered user. */
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
