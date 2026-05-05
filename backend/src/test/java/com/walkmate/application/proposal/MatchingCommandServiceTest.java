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
import com.walkmate.domain.walkintent.WalkIntent;
import com.walkmate.domain.walkintent.WalkIntentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MatchingCommandServiceTest {

    @Mock
    private WalkIntentRepository walkIntentRepository;

    @Mock
    private MatchProposalRepository matchProposalRepository;

    @Mock
    private WalkSessionRepository walkSessionRepository;

    @Mock
    private HotspotRepository hotspotRepository;

    @Mock
    private MatchingStrategy matchingStrategy;

    @Mock
    private NotificationPublisher notificationPublisher;

    @Mock
    private TransactionTemplate transactionTemplate;

    @Mock
    private ChatRoomRepository chatRoomRepository;

    private MatchingCommandService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new MatchingCommandService(
                walkIntentRepository,
                matchProposalRepository,
                walkSessionRepository,
                hotspotRepository,
                matchingStrategy,
                notificationPublisher,
                transactionTemplate,
                chatRoomRepository
        );
    }

    @Test
    void findOrCreateProposal_existingPending_returnsIt() {
        Instant now = Instant.now();
        WalkIntent intent = WalkIntent.create("h1", "user-a", now.plusSeconds(60), now.plusSeconds(3600), null, false, null, null);
        MatchProposal existing = MatchProposal.create(intent.getId(), "other-id", intent.getUserId(), "other-user", "h1", now.plusSeconds(120), now.plusSeconds(1500), now.plusSeconds(300));

        when(walkIntentRepository.findById(intent.getId())).thenReturn(Optional.of(intent));
        when(matchProposalRepository.findPendingByIntentId(intent.getId())).thenReturn(Optional.of(existing));

        Optional<MatchProposal> out = service.findOrCreateProposal(intent.getId(), intent.getUserId());

        assertThat(out).isPresent();
        assertThat(out.get()).isSameAs(existing);
    }

    @Test
    void findOrCreateProposal_noCandidate_returnsEmpty() {
        Instant now = Instant.now();
        WalkIntent intent = WalkIntent.create("h1", "user-b", now.plusSeconds(60), now.plusSeconds(3600), null, false, null, null);

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
        WalkIntent intent = WalkIntent.create("h1", "caller", now.plusSeconds(60), now.plusSeconds(3600), null, false, null, null);
        WalkIntent matched = WalkIntent.create("h1", "partner", now.plusSeconds(120), now.plusSeconds(1500), null, false, null, null);

        when(walkIntentRepository.findById(intent.getId())).thenReturn(Optional.of(intent));
        when(matchProposalRepository.findPendingByIntentId(intent.getId())).thenReturn(Optional.empty());
        when(matchingStrategy.findCandidates(intent)).thenReturn(List.of(matched));
        when(matchingStrategy.match(intent, List.of(matched))).thenReturn(Optional.of(new MatchResult(matched, now.plusSeconds(120), now.plusSeconds(1500), 10)));

        when(hotspotRepository.findById(intent.getHotspotId())).thenReturn(Optional.of(new Hotspot(intent.getHotspotId(), "H", 0.0, 0.0, 0)));

        // Save should return the same proposal instance
        when(matchProposalRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // Simulate re-loading intents under lock and saving
        when(walkIntentRepository.findByIdForUpdate(intent.getId())).thenReturn(Optional.of(intent));
        when(walkIntentRepository.findByIdForUpdate(matched.getId())).thenReturn(Optional.of(matched));
        when(walkIntentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Optional<MatchProposal> out = service.findOrCreateProposal(intent.getId(), intent.getUserId());

        assertThat(out).isPresent();
        MatchProposal created = out.get();
        assertThat(created.getIntentIdA()).isEqualTo(intent.getId());

        // After creation, intents should have been locked to MATCHING
        assertThat(intent.getStatus().name()).isEqualTo("MATCHING");
        assertThat(matched.getStatus().name()).isEqualTo("MATCHING");

        verify(matchProposalRepository).save(any());
        verify(walkIntentRepository).findByIdForUpdate(intent.getId());
        verify(walkIntentRepository).findByIdForUpdate(matched.getId());
        verify(notificationPublisher).publish(any(Notification.class));
    }
}
