package com.walkmate.application.proposal;

import com.walkmate.application.walkintent.MatchResult;
import com.walkmate.application.walkintent.MatchingStrategy;
import com.walkmate.domain.hotspot.Hotspot;
import com.walkmate.domain.hotspot.HotspotRepository;
import com.walkmate.domain.notification.Notification;
import com.walkmate.domain.proposal.MatchProposal;
import com.walkmate.domain.proposal.MatchProposalRepository;
import com.walkmate.domain.session.WalkSessionRepository;
import com.walkmate.domain.shared.NotificationPublisher;
import com.walkmate.domain.chat.ChatRoomRepository;
import com.walkmate.domain.walkintent.IntentStatus;
import com.walkmate.domain.walkintent.WalkIntent;
import com.walkmate.domain.walkintent.WalkIntentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MatchingCommandServiceTest {

    @Mock private WalkIntentRepository     walkIntentRepository;
    @Mock private MatchProposalRepository  matchProposalRepository;
    @Mock private WalkSessionRepository    walkSessionRepository;
    @Mock private HotspotRepository        hotspotRepository;
    @Mock private MatchingStrategy         matchingStrategy;
    @Mock private NotificationPublisher    notificationPublisher;
    @Mock private TransactionTemplate      transactionTemplate;
    @Mock private ChatRoomRepository       chatRoomRepository;

    private MatchingCommandService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new MatchingCommandService(
                walkIntentRepository, matchProposalRepository, walkSessionRepository,
                hotspotRepository, matchingStrategy, notificationPublisher,
                transactionTemplate, chatRoomRepository);
    }

    // ── findOrCreateProposal ──────────────────────────────────────────────────

    @Test
    void findOrCreateProposal_existingPending_returnsIt() {
        Instant now = Instant.now();
        WalkIntent intent = publicIntent("user-a", now.plusSeconds(60), now.plusSeconds(3600));
        MatchProposal existing = MatchProposal.create(
                intent.getId(), "other-id", intent.getUserId(), "other-user",
                "h1", now.plusSeconds(120), now.plusSeconds(1500), now.plusSeconds(300));

        when(walkIntentRepository.findById(intent.getId())).thenReturn(Optional.of(intent));
        when(matchProposalRepository.findPendingByIntentId(intent.getId())).thenReturn(Optional.of(existing));

        Optional<MatchProposal> out = service.findOrCreateProposal(intent.getId(), intent.getUserId());

        assertThat(out).isPresent();
        assertThat(out.get()).isSameAs(existing);
    }

    @Test
    void findOrCreateProposal_noCandidate_returnsEmpty() {
        Instant now = Instant.now();
        WalkIntent intent = publicIntent("user-b", now.plusSeconds(60), now.plusSeconds(3600));

        when(walkIntentRepository.findById(intent.getId())).thenReturn(Optional.of(intent));
        when(matchProposalRepository.findPendingByIntentId(intent.getId())).thenReturn(Optional.empty());
        when(matchingStrategy.findCandidates(intent)).thenReturn(List.of());
        when(matchingStrategy.match(intent, List.of())).thenReturn(Optional.empty());

        Optional<MatchProposal> out = service.findOrCreateProposal(intent.getId(), intent.getUserId());

        assertThat(out).isEmpty();
    }

    @Test
    void findOrCreateProposal_candidateFound_createsProposalAndLocksIntents() {
        Instant now = Instant.now();
        WalkIntent intent   = publicIntent("caller",  now.plusSeconds(60),  now.plusSeconds(3600));
        WalkIntent matched  = publicIntent("partner", now.plusSeconds(120), now.plusSeconds(1500));

        when(walkIntentRepository.findById(intent.getId())).thenReturn(Optional.of(intent));
        when(matchProposalRepository.findPendingByIntentId(intent.getId())).thenReturn(Optional.empty());
        when(matchingStrategy.findCandidates(intent)).thenReturn(List.of(matched));
        when(matchingStrategy.match(intent, List.of(matched))).thenReturn(
                Optional.of(new MatchResult(matched, now.plusSeconds(120), now.plusSeconds(1500), 10)));
        when(hotspotRepository.findById(intent.getHotspotId()))
                .thenReturn(Optional.of(new Hotspot(intent.getHotspotId(), "H", 0.0, 0.0, 0)));
        when(matchProposalRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(walkIntentRepository.findByIdForUpdate(intent.getId())).thenReturn(Optional.of(intent));
        when(walkIntentRepository.findByIdForUpdate(matched.getId())).thenReturn(Optional.of(matched));
        when(walkIntentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Optional<MatchProposal> out = service.findOrCreateProposal(intent.getId(), intent.getUserId());

        assertThat(out).isPresent();
        assertThat(out.get().getIntentIdA()).isEqualTo(intent.getId());
        assertThat(intent.getStatus()).isEqualTo(IntentStatus.MATCHING);
        assertThat(matched.getStatus()).isEqualTo(IntentStatus.MATCHING);
        verify(matchProposalRepository).save(any());
        verify(notificationPublisher).publish(any(Notification.class));
    }

    // ── passProposal ──────────────────────────────────────────────────────────

    @Test
    void passProposal_publicProposal_returnsBothIntentsToOpen() {
        Instant now = Instant.now();
        WalkIntent intentA = lockedPublicIntent("user-a", now.plusSeconds(60), now.plusSeconds(3600));
        WalkIntent intentB = lockedPublicIntent("user-b", now.plusSeconds(60), now.plusSeconds(3600));
        MatchProposal proposal = pendingProposal(intentA, intentB, "user-a", "user-b", now);

        stubProposalAndIntents(proposal, intentA, intentB);

        service.passProposal(proposal.getProposalId(), "user-a");

        assertThat(intentA.getStatus()).isEqualTo(IntentStatus.OPEN);
        assertThat(intentB.getStatus()).isEqualTo(IntentStatus.OPEN);
        assertThat(proposal.getStatus().name()).isEqualTo("REJECTED");
        // Caller excludes partner so the engine won't re-pair them (X-3, GAP-11)
        assertThat(intentA.getExcludedUserIds()).isNotEmpty();
    }

    @Test
    void passProposal_privateProposal_cancelsBothIntents() {
        Instant now = Instant.now();
        WalkIntent intentA = lockedPrivateIntent("user-a", "user-b", now.plusSeconds(60), now.plusSeconds(3600));
        WalkIntent intentB = lockedPrivateIntent("user-b", "user-a", now.plusSeconds(60), now.plusSeconds(3600));
        MatchProposal proposal = pendingProposal(intentA, intentB, "user-a", "user-b", now);

        stubProposalAndIntents(proposal, intentA, intentB);

        service.passProposal(proposal.getProposalId(), "user-b");

        assertThat(intentA.getStatus()).isEqualTo(IntentStatus.CANCELLED);
        assertThat(intentB.getStatus()).isEqualTo(IntentStatus.CANCELLED);
        assertThat(proposal.getStatus().name()).isEqualTo("REJECTED");
    }

    @Test
    void passProposal_mixedPrivateFlags_throwsIllegalState() {
        Instant now = Instant.now();
        // Invariant violation: one intent public, one private — should never happen in production
        WalkIntent intentA = lockedPublicIntent("user-a", now.plusSeconds(60), now.plusSeconds(3600));
        WalkIntent intentB = lockedPrivateIntent("user-b", "user-a", now.plusSeconds(60), now.plusSeconds(3600));
        MatchProposal proposal = pendingProposal(intentA, intentB, "user-a", "user-b", now);

        stubProposalAndIntents(proposal, intentA, intentB);

        assertThatThrownBy(() -> service.passProposal(proposal.getProposalId(), "user-a"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("mismatched isPrivate flags");
    }

    // ── cancelProposal ────────────────────────────────────────────────────────

    @Test
    void cancelProposal_publicProposal_cancelsCallerAndUnlocksPartner() {
        Instant now = Instant.now();
        WalkIntent intentA = lockedPublicIntent("user-a", now.plusSeconds(60), now.plusSeconds(3600));
        WalkIntent intentB = lockedPublicIntent("user-b", now.plusSeconds(60), now.plusSeconds(3600));
        MatchProposal proposal = pendingProposal(intentA, intentB, "user-a", "user-b", now);

        stubProposalAndIntents(proposal, intentA, intentB);

        service.cancelProposal(proposal.getProposalId(), "user-a");

        assertThat(intentA.getStatus()).isEqualTo(IntentStatus.CANCELLED);
        assertThat(intentB.getStatus()).isEqualTo(IntentStatus.OPEN);
        assertThat(proposal.getStatus().name()).isEqualTo("REJECTED");
    }

    @Test
    void cancelProposal_privateProposal_cancelsBothIntents() {
        Instant now = Instant.now();
        WalkIntent intentA = lockedPrivateIntent("user-a", "user-b", now.plusSeconds(60), now.plusSeconds(3600));
        WalkIntent intentB = lockedPrivateIntent("user-b", "user-a", now.plusSeconds(60), now.plusSeconds(3600));
        MatchProposal proposal = pendingProposal(intentA, intentB, "user-a", "user-b", now);

        stubProposalAndIntents(proposal, intentA, intentB);

        service.cancelProposal(proposal.getProposalId(), "user-a");

        assertThat(intentA.getStatus()).isEqualTo(IntentStatus.CANCELLED);
        assertThat(intentB.getStatus()).isEqualTo(IntentStatus.CANCELLED);
        assertThat(proposal.getStatus().name()).isEqualTo("REJECTED");
    }

    // ── sweepExpiredProposals ─────────────────────────────────────────────────

    @Test
    void sweepExpiredProposals_publicProposal_returnsIntentsToOpen() {
        Instant now = Instant.now();
        WalkIntent intentA = lockedPublicIntent("user-a", now.plusSeconds(60), now.plusSeconds(3600));
        WalkIntent intentB = lockedPublicIntent("user-b", now.plusSeconds(60), now.plusSeconds(3600));
        MatchProposal proposal = pendingProposal(intentA, intentB, "user-a", "user-b", now);

        stubSweep(proposal, intentA, intentB);

        service.sweepExpiredProposals();

        assertThat(intentA.getStatus()).isEqualTo(IntentStatus.OPEN);
        assertThat(intentB.getStatus()).isEqualTo(IntentStatus.OPEN);
        assertThat(proposal.getStatus().name()).isEqualTo("EXPIRED");
    }

    @Test
    void sweepExpiredProposals_privateProposal_cancelsBothIntents() {
        Instant now = Instant.now();
        WalkIntent intentA = lockedPrivateIntent("user-a", "user-b", now.plusSeconds(60), now.plusSeconds(3600));
        WalkIntent intentB = lockedPrivateIntent("user-b", "user-a", now.plusSeconds(60), now.plusSeconds(3600));
        MatchProposal proposal = pendingProposal(intentA, intentB, "user-a", "user-b", now);

        stubSweep(proposal, intentA, intentB);

        service.sweepExpiredProposals();

        assertThat(intentA.getStatus()).isEqualTo(IntentStatus.CANCELLED);
        assertThat(intentB.getStatus()).isEqualTo(IntentStatus.CANCELLED);
        assertThat(proposal.getStatus().name()).isEqualTo("EXPIRED");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static WalkIntent publicIntent(String userId, Instant start, Instant end) {
        return WalkIntent.create("h1", userId, start, end, null, false, null, null);
    }

    private static WalkIntent lockedPublicIntent(String userId, Instant start, Instant end) {
        WalkIntent i = publicIntent(userId, start, end);
        i.lock();
        return i;
    }

    private static WalkIntent lockedPrivateIntent(String userId, String friendId, Instant start, Instant end) {
        WalkIntent i = WalkIntent.create("h1", userId, start, end, null, true, friendId, null);
        i.lock();
        return i;
    }

    private static MatchProposal pendingProposal(
            WalkIntent a, WalkIntent b, String userIdA, String userIdB, Instant now) {
        return MatchProposal.create(
                a.getId(), b.getId(), userIdA, userIdB,
                "h1", now.plusSeconds(60), now.plusSeconds(3600), now.plusSeconds(300));
    }

    private void stubProposalAndIntents(MatchProposal proposal, WalkIntent intentA, WalkIntent intentB) {
        when(matchProposalRepository.findById(proposal.getProposalId())).thenReturn(Optional.of(proposal));
        when(matchProposalRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(walkIntentRepository.findById(intentA.getId())).thenReturn(Optional.of(intentA));
        when(walkIntentRepository.findById(intentB.getId())).thenReturn(Optional.of(intentB));
        when(walkIntentRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @SuppressWarnings("unchecked")
    private void stubSweep(MatchProposal proposal, WalkIntent intentA, WalkIntent intentB) {
        when(matchProposalRepository.findExpiredPending()).thenReturn(List.of(proposal));
        when(matchProposalRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(walkIntentRepository.findById(intentA.getId())).thenReturn(Optional.of(intentA));
        when(walkIntentRepository.findById(intentB.getId())).thenReturn(Optional.of(intentB));
        when(walkIntentRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            TransactionCallback<?> cb = inv.getArgument(0);
            return cb.doInTransaction(null);
        });
    }
}
