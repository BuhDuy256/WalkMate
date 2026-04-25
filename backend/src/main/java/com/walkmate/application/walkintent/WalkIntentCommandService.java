package com.walkmate.application.walkintent;

import com.walkmate.application.proposal.MatchingCommandService;
import com.walkmate.domain.hotspot.Hotspot;
import com.walkmate.domain.hotspot.HotspotErrorCode;
import com.walkmate.domain.hotspot.HotspotRepository;
import com.walkmate.domain.notification.Notification;
import com.walkmate.domain.notification.NotificationType;
import com.walkmate.domain.proposal.MatchProposal;
import com.walkmate.domain.proposal.MatchProposalRepository;
import com.walkmate.domain.session.WalkSessionRepository;
import com.walkmate.domain.shared.NotificationPublisher;
import com.walkmate.domain.shared.exception.DomainException;
import com.walkmate.domain.social.SocialRepository;
import com.walkmate.domain.walkintent.MatchingConstraints;
import com.walkmate.domain.walkintent.WalkIntent;
import com.walkmate.domain.walkintent.WalkIntentErrorCode;
import com.walkmate.domain.walkintent.WalkIntentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WalkIntentCommandService {

    private static final long PROPOSAL_TTL_MINUTES = 5;

    private final WalkIntentRepository    walkIntentRepository;
    private final HotspotRepository       hotspotRepository;
    private final WalkSessionRepository   walkSessionRepository;
    private final SocialRepository        socialRepository;
    private final MatchingCommandService  matchingCommandService;
    private final MatchProposalRepository matchProposalRepository;
    private final NotificationPublisher   notificationPublisher;
    private final TransactionTemplate     transactionTemplate;

    public CreateIntentResult createIntent(CreateWalkIntentCommand command) {
        // Private invite path — entire atomic flow (I-2, P-1(b), UC-15 Case B) in one transaction.
        // acceptProposal() is @Transactional(REQUIRED) so it joins this transaction boundary.
        if (command.isPrivate() && command.invitedFriendId() != null) {
            CreateIntentResult result = transactionTemplate.execute(status ->
                    createPrivateInviteIntent(command));
            if (result == null) {
                throw new IllegalStateException("Private invite transaction returned null");
            }
            return result;
        }

        // Public path — save intent in its own transaction, then attempt inline match.
        WalkIntent saved = transactionTemplate.execute(status -> {
            // 1. Validate the hotspot exists
            hotspotRepository.findById(command.hotspotId())
                    .orElseThrow(() -> new DomainException(HotspotErrorCode.HOTSPOT_NOT_FOUND));

            // 2a. Guard: no overlapping OPEN or MATCHING intent for this user in the same window (I-1)
            if (walkIntentRepository.hasOverlappingActiveIntent(
                    command.userId(), command.timeWindowStart(), command.timeWindowEnd())) {
                throw new DomainException(WalkIntentErrorCode.INTENT_OVERLAPPING);
            }

            // 2b. Guard: no overlapping PENDING or ACTIVE session for this user in the same window (I-1)
            if (walkSessionRepository.hasOverlappingActiveSession(
                    command.userId(), command.timeWindowStart(), command.timeWindowEnd())) {
                throw new DomainException(WalkIntentErrorCode.INTENT_OVERLAPPING_SESSION);
            }

            // 3. Rich domain model validates time range and constraints internally
            WalkIntent intent = WalkIntent.create(
                    command.hotspotId(),
                    command.userId(),
                    command.timeWindowStart(),
                    command.timeWindowEnd(),
                    new MatchingConstraints(command.ageMin(), command.ageMax(), command.preferredGender()),
                    false,
                    null,
                    command.description()
            );

            return walkIntentRepository.save(intent);
        });

        if (saved == null) {
            throw new IllegalStateException("Failed to persist walk intent");
        }

        // Inline match attempt — public path only (UC-15 Case A1 / A2).
        // Best-effort after intent commit: matching failures must not fail intent creation.
        try {
            Optional<MatchProposal> proposal = matchingCommandService.findOrCreateProposal(
                    saved.getId(), command.userId());
            if (proposal.isPresent()) {
                WalkIntent refreshed = walkIntentRepository.findById(saved.getId())
                        .orElse(saved);
                return new CreateIntentResult(refreshed, proposal.get());
            }
            return new CreateIntentResult(saved, null);
        } catch (Exception e) {
            log.warn("Inline match failed for intent {}: {}", saved.getId(), e.getMessage());
        }
        return new CreateIntentResult(saved, null);
    }

    /**
     * Atomic private invite flow (UC-15 Case B, I-2, P-1(b), gap 3.2).
     *
     * Called exclusively from within {@code transactionTemplate.execute()} — all 6 steps share
     * a single DB transaction. Any failure rolls back ALL changes: no orphaned intents,
     * no ghost proposals. Private intents are never OPEN: both are locked to MATCHING
     * immediately (I-4, I-7) so they never appear in public matching results.
     */
    private CreateIntentResult createPrivateInviteIntent(CreateWalkIntentCommand command) {
        String senderId   = command.userId();
        String receiverId = command.invitedFriendId();

        // Step 1 — Validate hotspot, sender overlap, and friendship (shared guards)
        hotspotRepository.findById(command.hotspotId())
                .orElseThrow(() -> new DomainException(HotspotErrorCode.HOTSPOT_NOT_FOUND));

        if (walkIntentRepository.hasOverlappingActiveIntent(
                senderId, command.timeWindowStart(), command.timeWindowEnd())) {
            throw new DomainException(WalkIntentErrorCode.INTENT_OVERLAPPING);
        }
        if (walkSessionRepository.hasOverlappingActiveSession(
                senderId, command.timeWindowStart(), command.timeWindowEnd())) {
            throw new DomainException(WalkIntentErrorCode.INTENT_OVERLAPPING_SESSION);
        }
        if (!socialRepository.areAcceptedFriends(
                UUID.fromString(senderId), UUID.fromString(receiverId))) {
            throw new DomainException(WalkIntentErrorCode.INTENT_PRIVATE_FRIEND_NOT_ACCEPTED);
        }

        // Step 2 — Create and persist sender intent (OPEN → MATCHING via lock())
        WalkIntent senderIntent = WalkIntent.create(
                command.hotspotId(), senderId,
                command.timeWindowStart(), command.timeWindowEnd(),
                new MatchingConstraints(command.ageMin(), command.ageMax(), command.preferredGender()),
                true, receiverId, command.description()
        );
        senderIntent.lock();
        WalkIntent savedSender = walkIntentRepository.save(senderIntent);

        // Step 3 — Overlap check for the receiver (I-1 applied to invited friend)
        if (walkIntentRepository.hasOverlappingActiveIntent(
                receiverId, command.timeWindowStart(), command.timeWindowEnd())) {
            throw new DomainException(WalkIntentErrorCode.INTENT_OVERLAPPING);
        }
        if (walkSessionRepository.hasOverlappingActiveSession(
                receiverId, command.timeWindowStart(), command.timeWindowEnd())) {
            throw new DomainException(WalkIntentErrorCode.INTENT_OVERLAPPING_SESSION);
        }

        // Step 4 — Create system-generated receiver intent (MATCHING, is_private=true, I-7).
        //          invitedFriendId on the receiver side points back to the sender.
        //          This intent never enters public OPEN — it is locked immediately.
        WalkIntent receiverIntent = WalkIntent.create(
                command.hotspotId(), receiverId,
                command.timeWindowStart(), command.timeWindowEnd(),
                new MatchingConstraints(command.ageMin(), command.ageMax(), command.preferredGender()),
                true, senderId, null
        );
        receiverIntent.lock();
        WalkIntent savedReceiver = walkIntentRepository.save(receiverIntent);

        // Step 5 — Create proposal (P-1(b))
        Hotspot hotspot = hotspotRepository.findById(command.hotspotId())
                .orElseThrow(() -> new DomainException(HotspotErrorCode.HOTSPOT_NOT_FOUND));

        MatchProposal proposal = MatchProposal.create(
                savedSender.getId(), savedReceiver.getId(),
                senderId, receiverId,
                hotspot.getLat(), hotspot.getLng(),
                command.timeWindowStart(), command.timeWindowEnd(),
                Instant.now().plus(PROPOSAL_TTL_MINUTES, ChronoUnit.MINUTES)
        );
        MatchProposal savedProposal = matchProposalRepository.save(proposal);

        // Step 6 — Auto-accept sender side (P-2: acceptProposal joins this transaction via REQUIRED).
        //          The receiver has not yet accepted, so bothAccepted=false — no session is created.
        //          The returned proposal reflects acceptedByA=true for an accurate response payload.
        MatchProposal autoAccepted = matchingCommandService.acceptProposal(
                savedProposal.getProposalId(), senderId);

        // Step 7 — Push notifications
        notificationPublisher.publish(Notification.create(
                senderId,
                NotificationType.INVITE_SENT,
                Map.of("proposalId", savedProposal.getProposalId())
        ));
        notificationPublisher.publish(Notification.create(
                receiverId,
                NotificationType.PROPOSAL_RECEIVED,
                Map.of("proposalId",   savedProposal.getProposalId(),
                       "intentId",     savedReceiver.getId(),
                       "senderUserId", senderId)
        ));

        return new CreateIntentResult(savedSender, autoAccepted);
    }

    @Transactional
    public void cancelIntent(String intentId, String callerId) {
        // 1. Load — throws INTENT_NOT_FOUND if missing
        WalkIntent intent = walkIntentRepository.findById(intentId)
                .orElseThrow(() -> new DomainException(WalkIntentErrorCode.INTENT_NOT_FOUND));

        // 2. Ownership check
        if (!intent.getUserId().equals(callerId)) {
            throw new DomainException(WalkIntentErrorCode.INTENT_NOT_OWNER);
        }

        // 3. Domain enforces state: throws if already CANCELLED or CONSUMED
        intent.cancel();

        // 4. Persist updated status (soft delete — row kept for audit)
        walkIntentRepository.save(intent);
    }
}
