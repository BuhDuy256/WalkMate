package com.walkmate.application.walkintent;

import com.walkmate.application.proposal.MatchingCommandService;
import com.walkmate.application.shared.MatchingTransactionExecutor;
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
import com.walkmate.infrastructure.lock.UserCalendarLockManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Creation and cancellation of walk intents.
 *
 * <p>Both the public and private creation paths serialize on the affected user's calendar
 * via {@link UserCalendarLockManager} before performing any overlap check or insert, which
 * is what prevents double-booking across public matching and direct invites.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WalkIntentCommandService {

    private static final long PROPOSAL_TTL_MINUTES = 5;

    private final WalkIntentRepository      walkIntentRepository;
    private final HotspotRepository         hotspotRepository;
    private final WalkSessionRepository     walkSessionRepository;
    private final SocialRepository          socialRepository;
    private final MatchingCommandService    matchingCommandService;
    private final MatchProposalRepository   matchProposalRepository;
    private final NotificationPublisher     notificationPublisher;
    private final UserCalendarLockManager   userCalendarLockManager;
    private final MatchingTransactionExecutor matchingTransactionExecutor;

    public CreateIntentResult createIntent(CreateWalkIntentCommand command) {
        // Private invite path — the entire atomic flow (both intents, proposal, sender
        // auto-accept) runs in one transaction, retried as a single unit on conflict.
        if (command.isPrivate() && command.invitedFriendId() != null) {
            CreateIntentResult result = matchingTransactionExecutor.execute(() ->
                    createPrivateInviteAttempt(command));
            if (result == null) {
                throw new IllegalStateException("Private invite transaction returned null");
            }
            return result;
        }

        // Public path — save intent in its own transaction, then attempt inline match.
        WalkIntent saved = matchingTransactionExecutor.execute(() ->
                createPublicIntentAttempt(command));
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

    private WalkIntent createPublicIntentAttempt(CreateWalkIntentCommand command) {
        // 1. Serialize on the creator's calendar before any overlap check or insert.
        userCalendarLockManager.lockUsers(List.of(command.userId()));

        // 2. Validate the hotspot exists.
        hotspotRepository.findById(command.hotspotId())
                .orElseThrow(() -> new DomainException(HotspotErrorCode.HOTSPOT_NOT_FOUND));

        // 3. Recheck overlap under the lock (I-1).
        if (walkIntentRepository.hasOverlappingActiveIntent(
                command.userId(), command.timeWindowStart(), command.timeWindowEnd())) {
            throw new DomainException(WalkIntentErrorCode.INTENT_OVERLAPPING);
        }
        if (walkSessionRepository.hasOverlappingActiveSession(
                command.userId(), command.timeWindowStart(), command.timeWindowEnd())) {
            throw new DomainException(WalkIntentErrorCode.INTENT_OVERLAPPING_SESSION);
        }

        // 4. Rich domain model validates time range and constraints internally.
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
    }

    /**
     * Atomic private invite flow (UC-15 Case B, I-2). All steps share a single database
     * transaction; any failure rolls back all changes. The nested sender auto-accept joins
     * this same transaction and is never retried independently.
     */
    private CreateIntentResult createPrivateInviteAttempt(CreateWalkIntentCommand command) {
        String senderId   = command.userId();
        String receiverId = command.invitedFriendId();

        // Step 1 — Serialize both participants' calendars (deterministic order inside manager).
        userCalendarLockManager.lockUsers(List.of(senderId, receiverId));

        // Step 2 — Validate hotspot and friendship.
        hotspotRepository.findById(command.hotspotId())
                .orElseThrow(() -> new DomainException(HotspotErrorCode.HOTSPOT_NOT_FOUND));
        if (!socialRepository.areAcceptedFriends(
                UUID.fromString(senderId), UUID.fromString(receiverId))) {
            throw new DomainException(WalkIntentErrorCode.INTENT_PRIVATE_FRIEND_NOT_ACCEPTED);
        }

        // Step 3 — Overlap checks for both participants, under their calendar locks.
        if (walkIntentRepository.hasOverlappingActiveIntent(
                senderId, command.timeWindowStart(), command.timeWindowEnd())) {
            throw new DomainException(WalkIntentErrorCode.INTENT_OVERLAPPING);
        }
        if (walkSessionRepository.hasOverlappingActiveSession(
                senderId, command.timeWindowStart(), command.timeWindowEnd())) {
            throw new DomainException(WalkIntentErrorCode.INTENT_OVERLAPPING_SESSION);
        }
        if (walkIntentRepository.hasOverlappingActiveIntent(
                receiverId, command.timeWindowStart(), command.timeWindowEnd())) {
            throw new DomainException(WalkIntentErrorCode.INTENT_OVERLAPPING);
        }
        if (walkSessionRepository.hasOverlappingActiveSession(
                receiverId, command.timeWindowStart(), command.timeWindowEnd())) {
            throw new DomainException(WalkIntentErrorCode.INTENT_OVERLAPPING_SESSION);
        }

        // Step 4 — Create and persist sender intent (OPEN → MATCHING via lock()).
        WalkIntent senderIntent = WalkIntent.create(
                command.hotspotId(), senderId,
                command.timeWindowStart(), command.timeWindowEnd(),
                new MatchingConstraints(command.ageMin(), command.ageMax(), command.preferredGender()),
                true, receiverId, command.description()
        );
        senderIntent.lock();
        WalkIntent savedSender = walkIntentRepository.save(senderIntent);

        // Step 5 — Create the system-generated receiver intent (MATCHING immediately).
        WalkIntent receiverIntent = WalkIntent.create(
                command.hotspotId(), receiverId,
                command.timeWindowStart(), command.timeWindowEnd(),
                new MatchingConstraints(command.ageMin(), command.ageMax(), command.preferredGender()),
                true, senderId, null
        );
        receiverIntent.lock();
        WalkIntent savedReceiver = walkIntentRepository.save(receiverIntent);

        // Step 6 — Create proposal (P-1(b)).
        MatchProposal proposal = MatchProposal.create(
                savedSender.getId(), savedReceiver.getId(),
                senderId, receiverId,
                command.hotspotId(),
                command.timeWindowStart(), command.timeWindowEnd(),
                Instant.now().plus(PROPOSAL_TTL_MINUTES, ChronoUnit.MINUTES)
        );
        MatchProposal savedProposal = matchProposalRepository.save(proposal);

        // Step 7 — Auto-accept sender side. Uses the shared internal workflow so the
        // acceptance joins this transaction (never retried independently).
        MatchProposal autoAccepted = matchingCommandService.acceptProposalInternal(
                savedProposal.getProposalId(), senderId);

        // Step 8 — Push notifications.
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

    /** Cancels an intent owned by the caller, closing any pending proposal if needed. */
    public void cancelIntent(String intentId, String callerId) {
        matchingCommandService.cancelIntent(intentId, callerId);
    }
}
