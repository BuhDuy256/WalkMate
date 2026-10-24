package com.walkmate.matching;

import com.walkmate.application.proposal.MatchingCommandService;
import com.walkmate.application.walkintent.CreateIntentResult;
import com.walkmate.application.walkintent.CreateWalkIntentCommand;
import com.walkmate.application.walkintent.WalkIntentCommandService;
import com.walkmate.domain.proposal.MatchProposal;
import com.walkmate.domain.shared.exception.DomainException;
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
 * Two independent application instances (separate Hikari pools, transaction managers and
 * service graphs) competing against the same PostgreSQL database. Verifies the per-user
 * advisory locks and deterministic row-lock ordering hold across process boundaries.
 */
class MatchingTwoInstanceTest extends PostgresMatchingTestBase {

    private static MatchingTestInstance instanceOne;
    private static MatchingTestInstance instanceTwo;
    private JdbcClient db;

    @BeforeAll
    static void startInstances() {
        instanceOne = MatchingTestInstance.start(jdbcUrl(), "instance-one");
        instanceTwo = MatchingTestInstance.start(jdbcUrl(), "instance-two");
    }

    @AfterAll
    static void stopInstances() {
        instanceOne.close();
        instanceTwo.close();
    }

    @BeforeEach
    void setUp() {
        db = instanceOne.jdbcClient();
        Fixtures.resetDatabase(db);
    }

    private static String userId(String tag) {
        return UUID.nameUUIDFromBytes(tag.getBytes()).toString();
    }

    private static Instant start(int h) {
        return Instant.now().plus(Duration.ofHours(h));
    }

    private static CreateWalkIntentCommand publicCreate(String u, String hotspot, Instant s, Instant e) {
        return new CreateWalkIntentCommand(hotspot, u, s, e, 18, 99, "ANY", false, null, "test");
    }

    private static CreateWalkIntentCommand privateInvite(String sender, String receiver,
                                                         String hotspot, Instant s, Instant e) {
        return new CreateWalkIntentCommand(hotspot, sender, s, e, 18, 99, "ANY", true, receiver, "test");
    }

    private void seedUserAndHotspot(String user, String hotspot) {
        Fixtures.seedUser(db, user, user.substring(0, 8) + "@example.com", "MALE");
        Fixtures.seedHotspot(db, hotspot, "H " + hotspot.substring(0, 8));
    }

    @Test
    void crossInstancePublicCreateVsPrivateInvite_forSameUser() throws Exception {
        String user = userId("x-create-user");
        String friend = userId("x-create-friend");
        String hotspot = UUID.randomUUID().toString();
        seedUserAndHotspot(user, hotspot);
        seedUserAndHotspot(friend, hotspot);
        Fixtures.seedFriendship(db, friend, user);
        Instant s = start(2);
        Instant e = start(3);

        WalkIntentCommandService one = instanceOne.walkIntentCommandService();
        WalkIntentCommandService two = instanceTwo.walkIntentCommandService();

        var results = runConcurrently(List.<java.util.function.Supplier<String>>of(
                () -> { try { one.createIntent(publicCreate(user, hotspot, s, e)); return "public"; }
                       catch (DomainException ex) { return "public:" + ex.getErrorCode(); } },
                () -> { try { two.createIntent(privateInvite(friend, user, hotspot, s, e)); return "private"; }
                       catch (DomainException ex) { return "private:" + ex.getErrorCode(); } }
        ));

        assertTrue(results.stream().allMatch(r -> r.error() == null), "unexpected infrastructure errors");
        long intents = Fixtures.countIntents(db);
        assertTrue(intents == 1 || intents == 2, "expected 1 (public) or 2 (private) intents, got " + intents);
        assertEquals(1, Fixtures.countActiveIntentsForUser(db, user));
    }

    @Test
    void crossInstanceConcurrentAccept_createsExactlyOneSession() throws Exception {
        String a = userId("x-accept-a");
        String b = userId("x-accept-b");
        String hotspot = UUID.randomUUID().toString();
        seedUserAndHotspot(a, hotspot);
        seedUserAndHotspot(b, hotspot);
        Instant s = start(2);
        Instant e = start(3);
        String intentA = UUID.randomUUID().toString();
        String intentB = UUID.randomUUID().toString();
        Fixtures.seedOpenIntent(db, intentA, hotspot, a, s, e);
        Fixtures.seedOpenIntent(db, intentB, hotspot, b, s, e);

        // Instance one creates the proposal.
        MatchingCommandService matchingOne = instanceOne.matchingCommandService();
        MatchProposal proposal = matchingOne.findOrCreateProposal(intentA, a).orElseThrow();
        String proposalId = proposal.getProposalId();

        MatchingCommandService one = matchingOne;
        MatchingCommandService two = instanceTwo.matchingCommandService();

        var results = runConcurrently(List.<java.util.function.Supplier<String>>of(
                () -> { try { one.acceptProposal(proposalId, a); return "accepted-a"; }
                       catch (DomainException ex) { return "a:" + ex.getErrorCode(); } },
                () -> { try { two.acceptProposal(proposalId, b); return "accepted-b"; }
                       catch (DomainException ex) { return "b:" + ex.getErrorCode(); } }
        ));

        assertTrue(results.stream().allMatch(r -> r.error() == null), "unexpected infrastructure errors");
        assertEquals("CONFIRMED", Fixtures.proposalStatus(db, proposalId));
        assertEquals(1, Fixtures.countSessions(db));
        assertEquals(2, Fixtures.countIntentsByStatus(db, "CONSUMED"));
    }

    @Test
    void crossInstanceCancelVsAccept_leavesNoStrandedIntent() throws Exception {
        String a = userId("x-race-a");
        String b = userId("x-race-b");
        String hotspot = UUID.randomUUID().toString();
        seedUserAndHotspot(a, hotspot);
        seedUserAndHotspot(b, hotspot);
        Instant s = start(2);
        Instant e = start(3);
        String intentA = UUID.randomUUID().toString();
        String intentB = UUID.randomUUID().toString();
        Fixtures.seedOpenIntent(db, intentA, hotspot, a, s, e);
        Fixtures.seedOpenIntent(db, intentB, hotspot, b, s, e);

        MatchingCommandService matchingOne = instanceOne.matchingCommandService();
        MatchProposal proposal = matchingOne.findOrCreateProposal(intentA, a).orElseThrow();
        String proposalId = proposal.getProposalId();

        MatchingCommandService two = instanceTwo.matchingCommandService();

        runConcurrently(List.<java.util.function.Supplier<String>>of(
                () -> { try { matchingOne.acceptProposal(proposalId, a); return "accepted"; }
                       catch (DomainException ignored) { return "accept-blocked"; } },
                () -> { try { two.cancelProposal(proposalId, b); return "cancelled"; }
                       catch (DomainException ignored) { return "cancel-blocked"; } }
        ));

        // No matter the interleaving, no intent may be left stranded in MATCHING.
        assertEquals(0, Fixtures.countIntentsByStatus(db, "MATCHING"));
        assertTrue(Fixtures.countSessions(db) <= 1, "at most one session");
    }
}
