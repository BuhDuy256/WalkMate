package com.walkmate.application.proposal;

import com.walkmate.application.chat.ChatRoomRepository;
import com.walkmate.application.notification.PushNotificationProvider;
import com.walkmate.application.walkintent.MatchingStrategy;
import com.walkmate.application.walkintent.MatchResult;
import com.walkmate.domain.hotspot.Hotspot;
import com.walkmate.domain.hotspot.HotspotErrorCode;
import com.walkmate.domain.hotspot.HotspotRepository;
import com.walkmate.domain.notification.Notification;
import com.walkmate.domain.notification.NotificationType;
import com.walkmate.domain.proposal.MatchProposal;
import com.walkmate.domain.proposal.MatchProposalRepository;
import com.walkmate.domain.proposal.ProposalErrorCode;
import com.walkmate.domain.session.WalkSession;
import com.walkmate.domain.session.WalkSessionRepository;
import com.walkmate.domain.shared.NotificationPublisher;
import com.walkmate.domain.shared.exception.DomainException;
import com.walkmate.domain.user.UserRepository;
import com.walkmate.domain.walkintent.IntentStatus;
import com.walkmate.domain.walkintent.WalkIntent;
import com.walkmate.domain.walkintent.WalkIntentErrorCode;
import com.walkmate.domain.walkintent.WalkIntentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatchingCommandService {

    private static final long PROPOSAL_TTL_MINUTES = 5;

    private final WalkIntentRepository     walkIntentRepository;
    private final MatchProposalRepository  matchProposalRepository;
    private final WalkSessionRepository    walkSessionRepository;
    private final HotspotRepository        hotspotRepository;
    private final UserRepository           userRepository;
    private final MatchingStrategy         matchingStrategy;
    private final NotificationPublisher    notificationPublisher;
    private final PushNotificationProvider pushNotificationProvider;
    private final TransactionTemplate      transactionTemplate;
    private final ChatRoomRepository       chatRoomRepository;

    // ── Find or create a proposal ─────────────────────────────────────────────

    /**
     * Looks for an existing PENDING proposal for this intent. If none exists,
     * runs the matching strategy and creates one if a candidate is found.
     *
     * @return the proposal, or empty if no candidate is available yet
     */
    @Transactional
    public Optional<MatchProposal> findOrCreateProposal(String intentId, String callerId) {
        // 1. Load intent — verify it is OPEN and owned by the caller
        WalkIntent intent = walkIntentRepository.findById(intentId)
                .orElseThrow(() -> new DomainException(WalkIntentErrorCode.INTENT_NOT_FOUND));

        if (!intent.getUserId().equals(callerId)) {
            throw new DomainException(WalkIntentErrorCode.INTENT_NOT_OWNER);
        }
        if (intent.getStatus() != IntentStatus.OPEN) {
            throw new DomainException(WalkIntentErrorCode.INVALID_INTENT_DATA,
                    "Intent must be OPEN to search for a match");
        }

        // 2. Return existing PENDING proposal if one already exists
        Optional<MatchProposal> existing = matchProposalRepository.findPendingByIntentId(intentId);
        if (existing.isPresent()) {
            return existing;
        }

        // 3. Run the two-stage matching strategy
        List<WalkIntent> candidates = matchingStrategy.findCandidates(intent);
        Optional<MatchResult> result = matchingStrategy.match(intent, candidates);
        if (result.isEmpty()) {
            return Optional.empty(); // still searching — caller returns 204
        }

        // 4. Resolve the hotspot coordinates for the proposed meeting point
        WalkIntent matched = result.get().matched();
        Hotspot hotspot = hotspotRepository.findById(intent.getHotspotId())
                .orElseThrow(() -> new DomainException(HotspotErrorCode.HOTSPOT_NOT_FOUND));

        // 5. Create and persist the proposal
        //    The unique partial index (V7) prevents duplicate PENDING proposals
        //    for the same pair if two users call findMatch simultaneously.
        MatchProposal proposal = MatchProposal.create(
                intentId,
                matched.getId(),
                intent.getUserId(),
                matched.getUserId(),
                hotspot.getLat(),
                hotspot.getLng(),
                result.get().overlapStart(),
                result.get().overlapEnd(),
                Instant.now().plus(PROPOSAL_TTL_MINUTES, ChronoUnit.MINUTES)
        );

        MatchProposal saved = matchProposalRepository.save(proposal);

        // 6. Re-acquire both intents under pessimistic locks before transitioning to MATCHING.
        //    Load in consistent lexicographic order (same strategy as acceptProposal) to prevent
        //    deadlock when two threads race on the same intent pair (I-4, P-1, GAP-2, Phase-0 Issue-1).
        String firstId  = intentId.compareTo(matched.getId()) <= 0 ? intentId : matched.getId();
        String secondId = firstId.equals(intentId) ? matched.getId() : intentId;

        WalkIntent lockedFirst = walkIntentRepository.findByIdForUpdate(firstId)
                .orElseThrow(() -> new DomainException(WalkIntentErrorCode.INTENT_NOT_FOUND));
        WalkIntent lockedSecond = walkIntentRepository.findByIdForUpdate(secondId)
                .orElseThrow(() -> new DomainException(WalkIntentErrorCode.INTENT_NOT_FOUND));

        // Re-verify both are still OPEN under the lock; a concurrent thread may have already
        // locked one of them between our initial read and this point.
        if (lockedFirst.getStatus() != IntentStatus.OPEN || lockedSecond.getStatus() != IntentStatus.OPEN) {
            throw new DomainException(WalkIntentErrorCode.INVALID_INTENT_DATA,
                    "One or both intents are no longer OPEN — cannot lock for matching");
        }

        lockedFirst.lock();
        lockedSecond.lock();
        walkIntentRepository.save(lockedFirst);
        walkIntentRepository.save(lockedSecond);

        // 1. Persist an in-app notification for the matched user's notification feed.
        notificationPublisher.publish(Notification.create(
                matched.getUserId(),
                NotificationType.PROPOSAL_RECEIVED,
                Map.of("proposalId", saved.getProposalId(),
                       "senderUserId", intent.getUserId())
        ));

        // 2. Send a real-time FCM push so the matched user's device navigates to
        //    the Matches → Proposal tab immediately (even if the app is backgrounded).
        //    The push uses a data-only payload so onMessageReceived() fires in all states.
        userRepository.findById(matched.getUserId()).ifPresent(matchedUser -> {
            String token = matchedUser.getFcmToken();
            if (token != null && !token.isBlank()) {
                pushNotificationProvider.sendMatchFound(
                        token,
                        matched.getId(),          // the matched user's own intent ID
                        saved.getProposalId()     // the newly created proposal ID
                );
            }
        });

        return Optional.of(saved);
    }

    // ── Accept proposal ───────────────────────────────────────────────────────

    /**
     * Records one user's acceptance. Returns the updated proposal.
     *
     * When BOTH users have accepted, executes the P-3 atomic session-creation
     * protocol under pessimistic locks to prevent double-consumption:
     *
     *   1. Lock both intent rows (SELECT ... FOR UPDATE) in a consistent order
     *      (by intentId lexicographic) to prevent deadlock.
     *   2. Re-verify both intents are still OPEN under lock.
     *   3. Consume both intents atomically.
     *   4. Confirm the proposal.
     *   5. Create the WalkSession — all in one database transaction.
     *
     * If either intent was already consumed by a concurrent transaction, a
     * PROPOSAL_INTENT_NO_LONGER_OPEN error is thrown; no session is created.
     */
    @Transactional
    public MatchProposal acceptProposal(String proposalId, String callerId) {
        // 1. Load the proposal
        MatchProposal proposal = matchProposalRepository.findById(proposalId)
                .orElseThrow(() -> new DomainException(ProposalErrorCode.PROPOSAL_NOT_FOUND));

        // 2. Determine which intent belongs to the caller
        String callerIntentId = proposal.resolveIntentIdForUser(callerId);
        if (callerIntentId == null) {
            throw new DomainException(ProposalErrorCode.PROPOSAL_NOT_PARTICIPANT);
        }

        // 3. Record acceptance (domain method guards against terminal state)
        boolean bothAccepted = proposal.recordAcceptance(callerIntentId);
        matchProposalRepository.save(proposal);

        if (!bothAccepted) {
            // Only one side has accepted so far — return updated proposal
            return proposal;
        }

        // ── P-3: BOTH ACCEPTED — CRITICAL SECTION ──────────────────────────
        // Lock both intents in a deterministic order to prevent deadlock.
        // If thread A locks (intent-1, intent-2) and thread B locks (intent-2, intent-1),
        // they can deadlock. Sorting by ID ensures both threads take locks in the same order.
        String firstId  = proposal.getIntentIdA().compareTo(proposal.getIntentIdB()) <= 0
                ? proposal.getIntentIdA() : proposal.getIntentIdB();
        String secondId = firstId.equals(proposal.getIntentIdA())
                ? proposal.getIntentIdB() : proposal.getIntentIdA();

        WalkIntent first = walkIntentRepository.findByIdForUpdate(firstId)
                .orElseThrow(() -> new DomainException(WalkIntentErrorCode.INTENT_NOT_FOUND));
        WalkIntent second = walkIntentRepository.findByIdForUpdate(secondId)
                .orElseThrow(() -> new DomainException(WalkIntentErrorCode.INTENT_NOT_FOUND));

        // Re-verify both are still MATCHING under the lock (P-2, GAP-7)
        if (first.getStatus() != IntentStatus.MATCHING || second.getStatus() != IntentStatus.MATCHING) {
            throw new DomainException(ProposalErrorCode.PROPOSAL_INTENT_NO_LONGER_OPEN);
        }

        // Consume both intents (domain method guards against double-consumption)
        first.consume();
        second.consume();
        walkIntentRepository.save(first);
        walkIntentRepository.save(second);

        // Confirm the proposal
        proposal.confirm(Instant.now());
        matchProposalRepository.save(proposal);

        // Create the walk session
        WalkSession session = WalkSession.create(
                proposal.getProposalId(),
                proposal.getUserIdA(),
                proposal.getUserIdB(),
                proposal.getProposedLocationLat(),
                proposal.getProposedLocationLng(),
                proposal.getProposedStartTime(),
                proposal.getProposedEndTime()
        );
        walkSessionRepository.save(session);

        // Register afterCommit hook — MongoDB write fires only once PostgreSQL commits.
        // MongoDB is a derived, eventually-consistent store. If this write fails, the
        // WalkSession remains valid in PostgreSQL (source of truth) and a reconciliation
        // job can re-initialize the missing chat room. (GAP-8, P-3 step 3)
        final String sessionId = session.getSessionId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    chatRoomRepository.initRoom(sessionId);
                } catch (Exception e) {
                    log.error("Chat room init failed after session creation: sessionId={} error={}",
                            sessionId, e.getMessage());
                }
            }
        });

        // Notify both participants that their session has been confirmed
        notificationPublisher.publish(Notification.create(
                proposal.getUserIdA(),
                NotificationType.SESSION_CONFIRMED,
                Map.of("sessionId", session.getSessionId(),
                       "partnerUserId", proposal.getUserIdB())
        ));
        notificationPublisher.publish(Notification.create(
                proposal.getUserIdB(),
                NotificationType.SESSION_CONFIRMED,
                Map.of("sessionId", session.getSessionId(),
                       "partnerUserId", proposal.getUserIdA())
        ));
        // ── END CRITICAL SECTION ────────────────────────────────────────────

        return proposal;
    }

    // ── Pass / reject proposal ────────────────────────────────────────────────

    @Transactional
    public void passProposal(String proposalId, String callerId) {
        MatchProposal proposal = matchProposalRepository.findById(proposalId)
                .orElseThrow(() -> new DomainException(ProposalErrorCode.PROPOSAL_NOT_FOUND));

        if (proposal.resolveIntentIdForUser(callerId) == null) {
            throw new DomainException(ProposalErrorCode.PROPOSAL_NOT_PARTICIPANT);
        }

        proposal.reject();
        matchProposalRepository.save(proposal);

        // Return both intents to OPEN so they re-enter the matching pool (GAP-3)
        WalkIntent intentA = walkIntentRepository.findById(proposal.getIntentIdA())
                .orElseThrow(() -> new DomainException(WalkIntentErrorCode.INTENT_NOT_FOUND));
        WalkIntent intentB = walkIntentRepository.findById(proposal.getIntentIdB())
                .orElseThrow(() -> new DomainException(WalkIntentErrorCode.INTENT_NOT_FOUND));
        intentA.unlock();
        intentB.unlock();
        walkIntentRepository.save(intentA);
        walkIntentRepository.save(intentB);
    }

    // ── Cancel proposal ───────────────────────────────────────────────────────

    /**
     * Hard-cancels a PENDING proposal on behalf of the caller.
     *
     * Unlike pass (which leaves both intents OPEN), cancel also closes the
     * caller's own intent — they are explicitly withdrawing from the search.
     * The partner's intent remains OPEN so they can be matched again.
     */
    @Transactional
    public void cancelProposal(String proposalId, String callerId) {
        MatchProposal proposal = matchProposalRepository.findById(proposalId)
                .orElseThrow(() -> new DomainException(ProposalErrorCode.PROPOSAL_NOT_FOUND));

        String callerIntentId = proposal.resolveIntentIdForUser(callerId);
        if (callerIntentId == null) {
            throw new DomainException(ProposalErrorCode.PROPOSAL_NOT_PARTICIPANT);
        }

        // Mark the proposal terminal — reuses the same guard as pass
        proposal.reject();
        matchProposalRepository.save(proposal);

        // Close the caller's intent — they are explicitly withdrawing from the search
        WalkIntent callerIntent = walkIntentRepository.findById(callerIntentId)
                .orElseThrow(() -> new DomainException(WalkIntentErrorCode.INTENT_NOT_FOUND));
        callerIntent.cancel();
        walkIntentRepository.save(callerIntent);

        // Return the partner's intent to OPEN so they can be matched again (GAP-3)
        String partnerIntentId = callerIntentId.equals(proposal.getIntentIdA())
                ? proposal.getIntentIdB()
                : proposal.getIntentIdA();
        WalkIntent partnerIntent = walkIntentRepository.findById(partnerIntentId)
                .orElseThrow(() -> new DomainException(WalkIntentErrorCode.INTENT_NOT_FOUND));
        partnerIntent.unlock();
        walkIntentRepository.save(partnerIntent);
    }

    // ── List proposals ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<MatchProposal> getPendingProposals(String userId) {
        return matchProposalRepository.findPendingForUser(userId);
    }

    // ── Scheduler sweep ───────────────────────────────────────────────────────

    /**
     * Called by the scheduler to expire overdue proposals and return both
     * intents to OPEN so participants re-enter the matching pool (P-4, GAP-3).
     *
     * Each proposal is processed in its own isolated transaction (Phase-0 Issue-2).
     * A failure on one proposal (e.g. OCC conflict) rolls back only that proposal's
     * changes and is logged; the sweep continues to the next proposal.
     * The outer method is intentionally not @Transactional — the TransactionTemplate
     * creates a fresh REQUIRED transaction per iteration.
     */
    public void sweepExpiredProposals() {
        List<MatchProposal> overdue = matchProposalRepository.findExpiredPending();
        for (MatchProposal proposal : overdue) {
            transactionTemplate.execute(status -> {
                proposal.expire();
                matchProposalRepository.save(proposal);

                walkIntentRepository.findById(proposal.getIntentIdA())
                        .filter(i -> i.getStatus() == IntentStatus.MATCHING)
                        .ifPresent(i -> { i.unlock(); walkIntentRepository.save(i); });

                walkIntentRepository.findById(proposal.getIntentIdB())
                        .filter(i -> i.getStatus() == IntentStatus.MATCHING)
                        .ifPresent(i -> { i.unlock(); walkIntentRepository.save(i); });

                return null;
            });
        }
    }
}
