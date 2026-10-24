package com.walkmate.matching;

import com.walkmate.application.proposal.MatchingCommandService;
import com.walkmate.application.walkintent.CreateIntentResult;
import com.walkmate.application.walkintent.CreateWalkIntentCommand;
import com.walkmate.application.walkintent.WalkIntentCommandService;
import com.walkmate.domain.proposal.MatchProposal;
import com.walkmate.domain.shared.exception.DomainException;
import com.walkmate.domain.walkintent.WalkIntentErrorCode;
import com.walkmate.matching.support.Fixtures;
import com.walkmate.matching.support.MatchingTestInstance;
import com.walkmate.matching.support.PostgresMatchingTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Creation races and lifecycle races for a single application instance (one connection pool,
 * many competing threads). Verifies invariants I-01, I-02, I-03, I-04 and I-05 against real
 * PostgreSQL.
 */
class MatchingCreationRaceTest extends PostgresMatchingTestBase {

    private static MatchingTestInstance instance;
    private JdbcClient db;
    private WalkIntentCommandService intents;
    private MatchingCommandService matching;

    @BeforeAll
    static void startInstance() {
        instance = MatchingTestInstance.start(jdbcUrl(), "instance-one");
    }

    @AfterAll
    static void stopInstance() {
        instance.close();
    }

    @BeforeEach
    void setUp() {
        db = instance.jdbcClient();
        Fixtures.resetDatabase(db);
        intents = instance.walkIntentCommandService();
        matching = instance.matchingCommandService();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String userId(String tag) {
        return UUID.nameUUIDFromBytes(tag.getBytes()).toString();
    }

    private static Instant start(int hoursFromNow) {
        return Instant.now().plus(Duration.ofHours(hoursFromNow));
    }

    private static CreateWalkIntentCommand publicCreate(String user, String hotspot, Instant s, Instant e) {
        return new CreateWalkIntentCommand(hotspot, user, s, e, 18, 99, "ANY", false, null, "test");
    }

    private static CreateWalkIntentCommand privateInvite(String sender, String receiver,
                                                         String hotspot, Instant s, Instant e) {
        return new CreateWalkIntentCommand(hotspot, sender, s, e, 18, 99, "ANY", true, receiver, "test");
    }

    private void seedUserAndHotspot(String user, String hotspot) {
        Fixtures.seedUser(db, user, user.substring(0, 8) + "@example.com", "MALE");
        Fixtures.seedHotspot(db, hotspot, "Hotspot " + hotspot.substring(0, 8));
    }

    private static boolean isOverlapError(Throwable t) {
        return t instanceof DomainException de
                && de.getErrorCode() == WalkIntentErrorCode.INTENT_OVERLAPPING;
    }

    // ── Creation races (I-02) ─────────────────────────────────────────────────

    @Test
    void concurrentPublicCreatesForSameUser_yieldExactlyOneActiveIntent() throws Exception {
        String user = userId("public-public");
        String hotspot = UUID.randomUUID().toString();
        seedUserAndHotspot(user, hotspot);
        Instant s = start(2);
        Instant e = start(3);

        var results = runConcurrently(2, () -> {
            try {
                return intents.createIntent(publicCreate(user, hotspot, s, e));
            } catch (DomainException ex) {
                throw ex;
            }
        });

        long succeeded = results.stream().filter(r -> r.error() == null).count();
        long overlapped = results.stream().filter(r -> isOverlapError(r.error())).count();
        assertEquals(1, succeeded, "exactly one create must succeed");
        assertEquals(1, overlapped, "exactly one create must report overlap");
        assertEquals(1, Fixtures.countActiveIntentsForUser(db, user),
                "user must have exactly one active intent");
    }

    @Test
    void publicVsPrivateOverlappingForSameUser_yieldOneReservation() throws Exception {
        String user = userId("public-private");
        String friend = userId("public-private-friend");
        String hotspot = UUID.randomUUID().toString();
        seedUserAndHotspot(user, hotspot);
        seedUserAndHotspot(friend, hotspot);
        Fixtures.seedFriendship(db, friend, user);
        Instant s = start(2);
        Instant e = start(3);

        var results = runConcurrently(
                () -> {
                    try { intents.createIntent(publicCreate(user, hotspot, s, e)); return "public"; }
                    catch (DomainException ex) { return "public:error:" + ex.getErrorCode(); }
                },
                () -> {
                    try { intents.createIntent(privateInvite(friend, user, hotspot, s, e)); return "private"; }
                    catch (DomainException ex) { return "private:error:" + ex.getErrorCode(); }
                }
        );

        // Exactly one reservation survives: either the public OPEN intent or the private pair.
        long totalIntents = Fixtures.countIntents(db);
        assertTrue(totalIntents == 1 || totalIntents == 2,
                "expected 1 (public) or 2 (private) intents, got " + totalIntents);
        assertEquals(1, Fixtures.countActiveIntentsForUser(db, user),
                "user must have exactly one active intent after the race");
    }

    @Test
    void swappedPrivateInvites_yieldExactlyOnePairing() throws Exception {
        String a = userId("swapped-a");
        String b = userId("swapped-b");
        String hotspot = UUID.randomUUID().toString();
        seedUserAndHotspot(a, hotspot);
        seedUserAndHotspot(b, hotspot);
        Fixtures.seedFriendship(db, a, b);
        Fixtures.seedFriendship(db, b, a);
        Instant s = start(2);
        Instant e = start(3);

        var results = runConcurrently(
                () -> { try { return intents.createIntent(privateInvite(a, b, hotspot, s, e)); } catch (DomainException ex) { return null; } },
                () -> { try { return intents.createIntent(privateInvite(b, a, hotspot, s, e)); } catch (DomainException ex) { return null; } }
        );

        long succeeded = results.stream().filter(r -> r.error() == null && r.value() != null).count();
        assertEquals(1, succeeded, "only one of the swapped invites may succeed");
        assertEquals(1, Fixtures.countProposals(db), "exactly one proposal");
        assertEquals(2, Fixtures.countIntents(db), "exactly two MATCHING intents");
        assertEquals(2, Fixtures.countIntentsByStatus(db, "MATCHING"));
        assertEquals(1, Fixtures.countActiveIntentsForUser(db, a));
        assertEquals(1, Fixtures.countActiveIntentsForUser(db, b));
    }

    @Test
    void privateInviteWithReceiverSessionOverlap_rollsBackCompletely() {
        String sender = userId("session-overlap-sender");
        String receiver = userId("session-overlap-receiver");
        String other = userId("session-overlap-other");
        String hotspot = UUID.randomUUID().toString();
        seedUserAndHotspot(sender, hotspot);
        seedUserAndHotspot(receiver, hotspot);
        seedUserAndHotspot(other, hotspot);
        Fixtures.seedFriendship(db, sender, receiver);
        Instant s = start(2);
        Instant e = start(3);

        // Receiver already has a PENDING session overlapping the window, backed by a
        // proposal whose (already-consumed) intents belong to receiver + other. The intents
        // are CONSUMED so they do not trip the intent-overlap check; only the session check
        // should fire.
        Instant wideStart = s.minus(Duration.ofHours(1));
        Instant wideEnd = e.plus(Duration.ofHours(1));
        String ia = UUID.randomUUID().toString();
        String ib = UUID.randomUUID().toString();
        Fixtures.seedOpenIntent(db, ia, hotspot, other, wideStart, wideEnd);
        Fixtures.seedOpenIntent(db, ib, hotspot, receiver, wideStart, wideEnd);
        db.sql("UPDATE walk_intent SET status = 'CONSUMED' WHERE intent_id IN (:a, :b)")
                .param("a", UUID.fromString(ia))
                .param("b", UUID.fromString(ib))
                .update();
        String propId = UUID.randomUUID().toString();
        Fixtures.seedProposal(db, propId, ia, ib, hotspot, wideStart, wideEnd);
        Fixtures.seedPendingSession(db, UUID.randomUUID().toString(), propId, receiver, other, hotspot, s, e);

        DomainException ex = assertThrows(DomainException.class,
                () -> intents.createIntent(privateInvite(sender, receiver, hotspot, s, e)));
        assertEquals(WalkIntentErrorCode.INTENT_OVERLAPPING_SESSION, ex.getErrorCode());
        assertEquals(1, Fixtures.countProposals(db), "private invite must not create a NEW proposal");
        assertEquals(0, Fixtures.countIntentsByStatus(db, "MATCHING"),
                "private invite must not have created partial MATCHING intents");
    }

    @Test
    void adjacentWindowsForSameUser_areBothAllowed() {
        String user = userId("adjacent");
        String hotspot = UUID.randomUUID().toString();
        seedUserAndHotspot(user, hotspot);
        Instant s1 = start(2);
        Instant e1 = start(3);
        Instant s2 = e1; // adjacent — [2,3) and [3,4)
        Instant e2 = start(4);

        CreateIntentResult r1 = intents.createIntent(publicCreate(user, hotspot, s1, e1));
        CreateIntentResult r2 = intents.createIntent(publicCreate(user, hotspot, s2, e2));

        assertNotNull(r1.intent());
        assertNotNull(r2.intent());
        assertEquals(2, Fixtures.countActiveIntentsForUser(db, user));
    }

    @Test
    void concurrentPublicCreatesForDifferentUsers_bothSucceed() throws Exception {
        String u1 = userId("diff-u1");
        String u2 = userId("diff-u2");
        String hotspot = UUID.randomUUID().toString();
        seedUserAndHotspot(u1, hotspot);
        seedUserAndHotspot(u2, hotspot);
        Instant s = start(2);
        Instant e = start(3);

        var results = runConcurrently(
                () -> { try { return intents.createIntent(publicCreate(u1, hotspot, s, e)); } catch (DomainException ex) { return null; } },
                () -> { try { return intents.createIntent(publicCreate(u2, hotspot, s, e)); } catch (DomainException ex) { return null; } }
        );

        assertEquals(2, results.stream().filter(r -> r.error() == null && r.value() != null).count());
        assertEquals(2, Fixtures.countIntents(db));
    }

    // ── Lifecycle races ───────────────────────────────────────────────────────

    private record LifecycleSetup(String proposalId, String intentA, String intentB,
                                  String userA, String userB) {
    }

    private LifecycleSetup seedMatchedPair(String tagA, String tagB) {
        String userA = userId(tagA);
        String userB = userId(tagB);
        String hotspot = UUID.randomUUID().toString();
        seedUserAndHotspot(userA, hotspot);
        seedUserAndHotspot(userB, hotspot);
        Instant s = start(2);
        Instant e = start(3);
        String intentA = UUID.randomUUID().toString();
        String intentB = UUID.randomUUID().toString();
        Fixtures.seedOpenIntent(db, intentA, hotspot, userA, s, e);
        Fixtures.seedOpenIntent(db, intentB, hotspot, userB, s, e);
        MatchProposal proposal = matching.findOrCreateProposal(intentA, userA).orElseThrow();
        return new LifecycleSetup(proposal.getProposalId(), intentA, intentB, userA, userB);
    }

    @Test
    void concurrentAcceptByBothUsers_createsExactlyOneSession() throws Exception {
        LifecycleSetup setup = seedMatchedPair("accept-a", "accept-b");

        var results = runConcurrently(
                () -> { try { return matching.acceptProposal(setup.proposalId(), setup.userA()); } catch (DomainException ex) { return null; } },
                () -> { try { return matching.acceptProposal(setup.proposalId(), setup.userB()); } catch (DomainException ex) { return null; } }
        );

        assertEquals(2, results.stream().filter(r -> r.error() == null && r.value() != null).count());
        assertEquals("CONFIRMED", Fixtures.proposalStatus(db, setup.proposalId()));
        assertEquals(1, Fixtures.countSessions(db), "exactly one session (I-04)");
        assertEquals(2, Fixtures.countIntentsByStatus(db, "CONSUMED"));
    }

    @Test
    void duplicateProposalAttempts_yieldSingleProposal() throws Exception {
        String userA = userId("dup-a");
        String userB = userId("dup-b");
        String hotspot = UUID.randomUUID().toString();
        seedUserAndHotspot(userA, hotspot);
        seedUserAndHotspot(userB, hotspot);
        Instant s = start(2);
        Instant e = start(3);
        String intentA = UUID.randomUUID().toString();
        String intentB = UUID.randomUUID().toString();
        Fixtures.seedOpenIntent(db, intentA, hotspot, userA, s, e);
        Fixtures.seedOpenIntent(db, intentB, hotspot, userB, s, e);

        var results = runConcurrently(
                () -> matching.findOrCreateProposal(intentA, userA),
                () -> matching.findOrCreateProposal(intentB, userB)
        );

        assertTrue(results.stream().allMatch(r -> r.error() == null), "no unexpected errors");
        assertEquals(1, Fixtures.countProposals(db), "exactly one proposal (I-01)");
        assertEquals(2, Fixtures.countIntentsByStatus(db, "MATCHING"));
        List<MatchProposal> proposals = results.stream().filter(r -> r.value().isPresent())
                .map(r -> r.value().get()).toList();
        assertFalse(proposals.isEmpty());
        String pid = proposals.get(0).getProposalId();
        assertTrue(proposals.stream().allMatch(p -> p.getProposalId().equals(pid)));
    }

    @Test
    void concurrentAcceptAndPass_leavesNoStrandedMatchingIntent() throws Exception {
        LifecycleSetup setup = seedMatchedPair("accept-pass-a", "accept-pass-b");

        runConcurrently(
                () -> { try { matching.acceptProposal(setup.proposalId(), setup.userA()); return "accepted"; } catch (DomainException ignored) { return "accept-blocked"; } },
                () -> { try { matching.passProposal(setup.proposalId(), setup.userB()); return "passed"; } catch (DomainException ignored) { return "pass-blocked"; } }
        );

        assertEquals(0, Fixtures.countSessions(db), "no session may be created on reject");
        assertEquals(0, Fixtures.countIntentsByStatus(db, "MATCHING"), "no stranded MATCHING intent (I-05)");
    }

    @Test
    void concurrentAcceptAndCancel_leavesNoStrandedMatchingIntent() throws Exception {
        LifecycleSetup setup = seedMatchedPair("accept-cancel-a", "accept-cancel-b");

        runConcurrently(
                () -> { try { matching.acceptProposal(setup.proposalId(), setup.userA()); return "accepted"; } catch (DomainException ignored) { return "accept-blocked"; } },
                () -> { try { matching.cancelProposal(setup.proposalId(), setup.userB()); return "cancelled"; } catch (DomainException ignored) { return "cancel-blocked"; } }
        );

        assertEquals(0, Fixtures.countSessions(db), "no session may be created on cancel");
        assertEquals(0, Fixtures.countIntentsByStatus(db, "MATCHING"), "no stranded MATCHING intent (I-05)");
    }

    @Test
    void acceptAfterProposalExpired_isRejected() {
        LifecycleSetup setup = seedMatchedPair("expired-a", "expired-b");
        // Force the proposal to already be expired.
        db.sql("UPDATE match_proposal SET expires_at = now() - interval '1 minute' WHERE proposal_id = :id")
                .param("id", UUID.fromString(setup.proposalId()))
                .update();

        DomainException ex = assertThrows(DomainException.class,
                () -> matching.acceptProposal(setup.proposalId(), setup.userA()));
        assertEquals("PROPOSAL_ALREADY_TERMINAL", ex.getErrorCode().getCode());
        assertEquals(0, Fixtures.countSessions(db));
    }

    @Test
    void acceptReplayAfterConfirmation_isIdempotent() throws Exception {
        LifecycleSetup setup = seedMatchedPair("replay-a", "replay-b");
        matching.acceptProposal(setup.proposalId(), setup.userA());
        matching.acceptProposal(setup.proposalId(), setup.userB());
        assertEquals(1, Fixtures.countSessions(db));

        // Replay from either participant must not create another session or error.
        assertDoesNotThrow(() -> matching.acceptProposal(setup.proposalId(), setup.userA()));
        assertDoesNotThrow(() -> matching.acceptProposal(setup.proposalId(), setup.userB()));
        assertEquals(1, Fixtures.countSessions(db));
    }

    @Test
    void cancelOpenIntent_marksItCancelled() {
        String user = userId("cancel-open");
        String hotspot = UUID.randomUUID().toString();
        seedUserAndHotspot(user, hotspot);
        CreateIntentResult created = intents.createIntent(publicCreate(user, hotspot, start(2), start(3)));

        intents.cancelIntent(created.intent().getId(), user);

        assertEquals("CANCELLED", Fixtures.intentStatus(db, created.intent().getId()));
    }

    @Test
    void cancelMatchingIntent_closesProposalAndReleasesPartner() {
        LifecycleSetup setup = seedMatchedPair("cancel-match-a", "cancel-match-b");

        // Cancel user A's MATCHING intent — must close the proposal and release partner.
        intents.cancelIntent(setup.intentA(), setup.userA());

        assertEquals("CANCELLED", Fixtures.intentStatus(db, setup.intentA()));
        assertEquals("OPEN", Fixtures.intentStatus(db, setup.intentB()), "partner must return to OPEN (public)");
        assertFalse(Fixtures.proposalStatus(db, setup.proposalId()).equals("PENDING"),
                "proposal must no longer be PENDING");
    }

    @Test
    void passProposal_reopensBothIntentsAndExcludesPartner() {
        LifecycleSetup setup = seedMatchedPair("pass-a", "pass-b");
        matching.passProposal(setup.proposalId(), setup.userA());

        assertEquals("OPEN", Fixtures.intentStatus(db, setup.intentA()));
        assertEquals("OPEN", Fixtures.intentStatus(db, setup.intentB()));
        assertEquals("REJECTED", Fixtures.proposalStatus(db, setup.proposalId()));
    }

    @Test
    void cancelProposal_public_cancelsCallerAndReopensPartner() {
        LifecycleSetup setup = seedMatchedPair("cancel-prop-a", "cancel-prop-b");
        matching.cancelProposal(setup.proposalId(), setup.userA());

        assertEquals("CANCELLED", Fixtures.intentStatus(db, setup.intentA()));
        assertEquals("OPEN", Fixtures.intentStatus(db, setup.intentB()));
        assertEquals("REJECTED", Fixtures.proposalStatus(db, setup.proposalId()));
    }

    @Test
    void sweepExpiredProposals_releasesBothPublicIntents() {
        LifecycleSetup setup = seedMatchedPair("sweep-a", "sweep-b");
        db.sql("UPDATE match_proposal SET expires_at = now() - interval '1 minute' WHERE proposal_id = :id")
                .param("id", UUID.fromString(setup.proposalId()))
                .update();

        matching.sweepExpiredProposals();

        assertEquals("EXPIRED", Fixtures.proposalStatus(db, setup.proposalId()));
        assertEquals("OPEN", Fixtures.intentStatus(db, setup.intentA()));
        assertEquals("OPEN", Fixtures.intentStatus(db, setup.intentB()));
    }

    @Test
    void expireIntent_cascadesToPendingProposalAndReleasesPartner() {
        LifecycleSetup setup = seedMatchedPair("intent-expire-a", "intent-expire-b");

        // Make the walk window overdue so the expiry rule applies.
        db.sql("""
                        UPDATE walk_intent
                        SET time_window_start = now() - interval '10 minutes',
                            time_window_end   = now() - interval '5 minutes'
                        WHERE intent_id IN (:a, :b)
                        """)
                .param("a", UUID.fromString(setup.intentA()))
                .param("b", UUID.fromString(setup.intentB()))
                .update();

        matching.expireIntent(setup.intentA());

        assertEquals("EXPIRED", Fixtures.intentStatus(db, setup.intentA()));
        assertEquals("OPEN", Fixtures.intentStatus(db, setup.intentB()));
        assertEquals("EXPIRED", Fixtures.proposalStatus(db, setup.proposalId()));
    }

    @Test
    void privateInvite_cancelsBothIntentsOnPass() {
        String a = userId("priv-pass-a");
        String b = userId("priv-pass-b");
        String hotspot = UUID.randomUUID().toString();
        seedUserAndHotspot(a, hotspot);
        seedUserAndHotspot(b, hotspot);
        Fixtures.seedFriendship(db, a, b);
        Instant s = start(2);
        Instant e = start(3);

        CreateIntentResult created = intents.createIntent(privateInvite(a, b, hotspot, s, e));
        MatchProposal proposal = created.proposal();
        assertNotNull(proposal);

        matching.passProposal(proposal.getProposalId(), b);

        assertEquals("REJECTED", Fixtures.proposalStatus(db, proposal.getProposalId()));
        assertEquals(0, Fixtures.countIntentsByStatus(db, "MATCHING"));
        assertEquals(2, Fixtures.countIntentsByStatus(db, "CANCELLED"),
                "private pass cancels both intents (do not re-enter public pool)");
    }
}
