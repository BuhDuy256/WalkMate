package com.walkmate.application.proposal;

import com.walkmate.application.shared.MatchingTransactionExecutor;
import com.walkmate.application.shared.RetryableConcurrencyException;
import com.walkmate.application.walkintent.MatchingStrategy;
import com.walkmate.application.walkintent.MatchResult;
import com.walkmate.domain.chat.ChatRoomRepository;
import com.walkmate.domain.hotspot.HotspotErrorCode;
import com.walkmate.domain.hotspot.HotspotRepository;
import com.walkmate.domain.notification.Notification;
import com.walkmate.domain.notification.NotificationType;
import com.walkmate.domain.proposal.MatchProposal;
import com.walkmate.domain.proposal.MatchProposalRepository;
import com.walkmate.domain.proposal.ProposalErrorCode;
import com.walkmate.domain.proposal.ProposalStatus;
import com.walkmate.domain.session.WalkSession;
import com.walkmate.domain.session.WalkSessionRepository;
import com.walkmate.domain.shared.NotificationPublisher;
import com.walkmate.domain.shared.exception.DomainException;
import com.walkmate.domain.walkintent.IntentStatus;
import com.walkmate.domain.walkintent.WalkIntent;
import com.walkmate.domain.walkintent.WalkIntentErrorCode;
import com.walkmate.domain.walkintent.WalkIntentRepository;
import com.walkmate.infrastructure.lock.UserCalendarLockManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * All proposal/intent lifecycle writers.
 *
 * <p>Every writer follows the same canonical concurrency protocol (see
 * {@link UserCalendarLockManager} and {@code docs/implementation/matching-flow-decisions.md}):
 * discovery read (no locks) → lock users (advisory, sorted) → lock intents (row, sorted) →
 * lock proposal (row) → reload + revalidate → mutate. Transient conflicts are retried by
 * {@link MatchingTransactionExecutor} in fresh transactions; business failures are never
 * retried.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MatchingCommandService {

    private static final long PROPOSAL_TTL_MINUTES = 5;
    /** OPEN/MATCHING intents starting within this window are expired (walk starting soon). */
    private static final long INTENT_EXPIRY_BUFFER_MINUTES = 5;

    private final WalkIntentRepository      walkIntentRepository;
    private final MatchProposalRepository   matchProposalRepository;
    private final WalkSessionRepository     walkSessionRepository;
    private final HotspotRepository         hotspotRepository;
    private final MatchingStrategy          matchingStrategy;
    private final NotificationPublisher     notificationPublisher;
    private final ChatRoomRepository        chatRoomRepository;
    private final UserCalendarLockManager   userCalendarLockManager;
    private final MatchingTransactionExecutor matchingTransactionExecutor;

    // ── Find or create a proposal ─────────────────────────────────────────────

    /**
     * Looks for an existing PENDING proposal for this intent. If none exists, runs the
     * matching strategy and creates one if a candidate is found.
     *
     * @return the proposal, or empty if no candidate is available yet
     */
    public Optional<MatchProposal> findOrCreateProposal(String intentId, String callerId) {
        return matchingTransactionExecutor.execute(() -> findOrCreateProposalAttempt(intentId, callerId));
    }

    private Optional<MatchProposal> findOrCreateProposalAttempt(String intentId, String callerId) {
        // 1. Optimistic discovery — verify intent is OPEN and owned by the caller.
        WalkIntent intent = walkIntentRepository.findById(intentId)
                .orElseThrow(() -> new DomainException(WalkIntentErrorCode.INTENT_NOT_FOUND));

        if (!intent.getUserId().equals(callerId)) {
            throw new DomainException(WalkIntentErrorCode.INTENT_NOT_OWNER);
        }

        // 2. Return existing PENDING proposal if one already exists (even if this intent is
        //    now MATCHING — that is the expected state once a concurrent match has landed).
        Optional<MatchProposal> existing = matchProposalRepository.findPendingByIntentId(intentId);
        if (existing.isPresent()) {
            return existing;
        }

        if (intent.getStatus() != IntentStatus.OPEN) {
            throw new DomainException(WalkIntentErrorCode.INVALID_INTENT_DATA,
                    "Intent must be OPEN to search for a match");
        }

        // 3. Candidate search (before any lock is held — not part of the critical section).
        List<WalkIntent> candidates = matchingStrategy.findCandidates(intent);
        Optional<MatchResult> result = matchingStrategy.match(intent, candidates);
        if (result.isEmpty()) {
            return Optional.empty();
        }

        WalkIntent matched = result.get().matched();
        hotspotRepository.findById(intent.getHotspotId())
                .orElseThrow(() -> new DomainException(HotspotErrorCode.HOTSPOT_NOT_FOUND));

        // 4. Lock both users (advisory) before touching either calendar.
        userCalendarLockManager.lockUsers(List.of(intent.getUserId(), matched.getUserId()));

        // 5. Lock both intents in deterministic lexicographic order.
        WalkIntent[] pair = lockIntentsSorted(intentId, matched.getId());
        WalkIntent lockedCaller = pair[0].getId().equals(intentId) ? pair[0] : pair[1];
        WalkIntent lockedMatched = pair[0].getId().equals(intentId) ? pair[1] : pair[0];

        // 6. Revalidate under lock — both still OPEN and neither already has a PENDING proposal.
        if (lockedCaller.getStatus() != IntentStatus.OPEN || lockedMatched.getStatus() != IntentStatus.OPEN) {
            throw new RetryableConcurrencyException(
                    "One or both intents are no longer OPEN — reselecting candidate");
        }
        if (matchProposalRepository.findPendingByIntentId(lockedCaller.getId()).isPresent()
                || matchProposalRepository.findPendingByIntentId(lockedMatched.getId()).isPresent()) {
            throw new RetryableConcurrencyException(
                    "A PENDING proposal already exists for a participant");
        }
        // Revalidate the exclusion lists under lock (X-3 / GAP-11): a concurrent pass may have
        // added either user to the other's exclude list after the optimistic candidate search.
        if (isExcluded(lockedCaller, lockedMatched.getUserId())
                || isExcluded(lockedMatched, lockedCaller.getUserId())) {
            throw new RetryableConcurrencyException(
                    "Candidate is now excluded — reselecting candidate");
        }

        // 7. Create and persist the proposal, then transition both intents OPEN → MATCHING.
        MatchProposal proposal = MatchProposal.create(
                lockedCaller.getId(),
                lockedMatched.getId(),
                lockedCaller.getUserId(),
                lockedMatched.getUserId(),
                intent.getHotspotId(),
                result.get().overlapStart(),
                result.get().overlapEnd(),
                Instant.now().plus(PROPOSAL_TTL_MINUTES, ChronoUnit.MINUTES)
        );
        MatchProposal saved = matchProposalRepository.save(proposal);

        lockedCaller.lock();
        lockedMatched.lock();
        walkIntentRepository.save(lockedCaller);
        walkIntentRepository.save(lockedMatched);

        // Notify the matched user.
        notificationPublisher.publish(Notification.create(
                lockedMatched.getUserId(),
                NotificationType.PROPOSAL_RECEIVED,
                Map.of("proposalId",  saved.getProposalId(),
                       "intentId",    lockedMatched.getId(),
                       "senderUserId", lockedCaller.getUserId())
        ));

        return Optional.of(saved);
    }

    // ── Accept proposal ───────────────────────────────────────────────────────

    /**
     * Records one user's acceptance. When both users have accepted, atomically consumes
     * both intents, confirms the proposal and creates the walk session — all under the
     * canonical lock order so exactly one session is ever created per proposal.
     */
    public MatchProposal acceptProposal(String proposalId, String callerId) {
        return matchingTransactionExecutor.execute(() -> acceptProposalInternal(proposalId, callerId));
    }

    /**
     * Shared internal accept workflow. MUST be called within an active transaction —
     * either by {@link #acceptProposal} (which supplies one via the executor) or by the
     * private-invite flow (which reuses the enclosing transaction and must NOT retry the
     * acceptance independently).
     */
    public MatchProposal acceptProposalInternal(String proposalId, String callerId) {
        // 1. Discovery read (no locks) to identify participants.
        MatchProposal snapshot = matchProposalRepository.findById(proposalId)
                .orElseThrow(() -> new DomainException(ProposalErrorCode.PROPOSAL_NOT_FOUND));
        if (snapshot.resolveIntentIdForUser(callerId) == null) {
            throw new DomainException(ProposalErrorCode.PROPOSAL_NOT_PARTICIPANT);
        }

        // 2. Lock both users.
        userCalendarLockManager.lockUsers(List.of(snapshot.getUserIdA(), snapshot.getUserIdB()));

        // 3. Lock both intents in deterministic order.
        WalkIntent[] pair = lockIntentsSorted(snapshot.getIntentIdA(), snapshot.getIntentIdB());

        // 4. Lock the proposal row and re-read its authoritative state.
        MatchProposal proposal = matchProposalRepository.findByIdForUpdate(proposalId)
                .orElseThrow(() -> new DomainException(ProposalErrorCode.PROPOSAL_NOT_FOUND));

        // 5. Revalidate under lock.
        if (proposal.getStatus() == ProposalStatus.CONFIRMED) {
            return proposal; // idempotent replay after confirmation
        }
        if (proposal.getStatus() != ProposalStatus.PENDING) {
            throw new DomainException(ProposalErrorCode.PROPOSAL_ALREADY_TERMINAL);
        }
        if (!proposal.getExpiresAt().isAfter(Instant.now())) {
            throw new DomainException(ProposalErrorCode.PROPOSAL_ALREADY_TERMINAL,
                    "Proposal has expired");
        }

        String callerIntentId = proposal.resolveIntentIdForUser(callerId);
        if (callerIntentId == null) {
            throw new DomainException(ProposalErrorCode.PROPOSAL_NOT_PARTICIPANT);
        }

        // Verify both intents still MATCHING and still owned by this proposal.
        if (pair[0].getStatus() != IntentStatus.MATCHING || pair[1].getStatus() != IntentStatus.MATCHING) {
            throw new DomainException(ProposalErrorCode.PROPOSAL_INTENT_NO_LONGER_OPEN);
        }

        // Idempotent repeat from an already-accepted participant.
        boolean callerIsA = callerIntentId.equals(proposal.getIntentIdA());
        if ((callerIsA && proposal.isAcceptedByA()) || (!callerIsA && proposal.isAcceptedByB())) {
            return proposal;
        }

        // 6. Record acceptance.
        boolean bothAccepted = proposal.recordAcceptance(callerIntentId);
        matchProposalRepository.save(proposal);

        if (!bothAccepted) {
            return proposal;
        }

        // 7. Both accepted — consume intents, confirm proposal, create session.
        pair[0].consume();
        pair[1].consume();
        walkIntentRepository.save(pair[0]);
        walkIntentRepository.save(pair[1]);

        proposal.confirm(Instant.now());
        matchProposalRepository.save(proposal);

        WalkSession session = WalkSession.create(
                proposal.getProposalId(),
                proposal.getUserIdA(),
                proposal.getUserIdB(),
                proposal.getHotspotId(),
                proposal.getProposedStartTime(),
                proposal.getProposedEndTime()
        );
        walkSessionRepository.save(session);

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

        notificationPublisher.publish(Notification.create(
                proposal.getUserIdA(),
                NotificationType.SESSION_CONFIRMED,
                Map.of("sessionId", sessionId, "partnerUserId", proposal.getUserIdB())
        ));
        notificationPublisher.publish(Notification.create(
                proposal.getUserIdB(),
                NotificationType.SESSION_CONFIRMED,
                Map.of("sessionId", sessionId, "partnerUserId", proposal.getUserIdA())
        ));

        return proposal;
    }

    // ── Pass / reject proposal ────────────────────────────────────────────────

    /**
     * Rejects the proposal on behalf of the caller.
     * Public: both intents revert to OPEN; caller excludes partner (X-3).
     * Private: both intents → CANCELLED.
     */
    public void passProposal(String proposalId, String callerId) {
        matchingTransactionExecutor.execute(() -> {
            passProposalAttempt(proposalId, callerId);
            return null;
        });
    }

    private void passProposalAttempt(String proposalId, String callerId) {
        MatchProposal snapshot = matchProposalRepository.findById(proposalId)
                .orElseThrow(() -> new DomainException(ProposalErrorCode.PROPOSAL_NOT_FOUND));
        if (snapshot.resolveIntentIdForUser(callerId) == null) {
            throw new DomainException(ProposalErrorCode.PROPOSAL_NOT_PARTICIPANT);
        }

        userCalendarLockManager.lockUsers(List.of(snapshot.getUserIdA(), snapshot.getUserIdB()));

        WalkIntent[] pair = lockIntentsSorted(snapshot.getIntentIdA(), snapshot.getIntentIdB());

        MatchProposal proposal = matchProposalRepository.findByIdForUpdate(proposalId)
                .orElseThrow(() -> new DomainException(ProposalErrorCode.PROPOSAL_NOT_FOUND));
        if (proposal.getStatus() != ProposalStatus.PENDING) {
            return; // already terminal — idempotent no-op
        }

        proposal.reject();
        matchProposalRepository.save(proposal);
        transitionRejectedProposalIntents(proposal, pair[0], pair[1], callerId);
    }

    // ── Cancel proposal / intent ──────────────────────────────────────────────

    /**
     * Hard-cancels a PENDING proposal on behalf of the caller. The caller's own intent is
     * always CANCELLED. Public: partner reverts to OPEN; private: partner also CANCELLED.
     */
    public void cancelProposal(String proposalId, String callerId) {
        matchingTransactionExecutor.execute(() -> {
            cancelProposalAttempt(proposalId, callerId);
            return null;
        });
    }

    private void cancelProposalAttempt(String proposalId, String callerId) {
        MatchProposal snapshot = matchProposalRepository.findById(proposalId)
                .orElseThrow(() -> new DomainException(ProposalErrorCode.PROPOSAL_NOT_FOUND));
        String callerIntentId = snapshot.resolveIntentIdForUser(callerId);
        if (callerIntentId == null) {
            throw new DomainException(ProposalErrorCode.PROPOSAL_NOT_PARTICIPANT);
        }

        userCalendarLockManager.lockUsers(List.of(snapshot.getUserIdA(), snapshot.getUserIdB()));

        WalkIntent[] pair = lockIntentsSorted(snapshot.getIntentIdA(), snapshot.getIntentIdB());

        MatchProposal proposal = matchProposalRepository.findByIdForUpdate(proposalId)
                .orElseThrow(() -> new DomainException(ProposalErrorCode.PROPOSAL_NOT_FOUND));
        if (proposal.getStatus() != ProposalStatus.PENDING) {
            return; // already terminal — idempotent no-op
        }

        proposal.reject();
        matchProposalRepository.save(proposal);

        String partnerIntentId = callerIntentId.equals(proposal.getIntentIdA())
                ? proposal.getIntentIdB() : proposal.getIntentIdA();
        WalkIntent callerIntent = callerIntentId.equals(pair[0].getId()) ? pair[0] : pair[1];
        WalkIntent partnerIntent = partnerIntentId.equals(pair[0].getId()) ? pair[0] : pair[1];

        ProposalFlowType flowType = resolveProposalFlowType(callerIntent, partnerIntent);
        callerIntent.cancel();
        walkIntentRepository.save(callerIntent);
        if (flowType == ProposalFlowType.PRIVATE) {
            partnerIntent.cancel();
        } else {
            partnerIntent.unlock();
        }
        walkIntentRepository.save(partnerIntent);
    }

    /**
     * Cancels a single intent owned by the caller. When the intent is MATCHING, the
     * corresponding PENDING proposal is closed in the same transaction so the partner is
     * never left stranded in MATCHING.
     */
    public void cancelIntent(String intentId, String callerId) {
        matchingTransactionExecutor.execute(() -> {
            cancelIntentAttempt(intentId, callerId);
            return null;
        });
    }

    private void cancelIntentAttempt(String intentId, String callerId) {
        WalkIntent intent = walkIntentRepository.findById(intentId)
                .orElseThrow(() -> new DomainException(WalkIntentErrorCode.INTENT_NOT_FOUND));
        if (!intent.getUserId().equals(callerId)) {
            throw new DomainException(WalkIntentErrorCode.INTENT_NOT_OWNER);
        }
        if (intent.getStatus() == IntentStatus.CANCELLED
                || intent.getStatus() == IntentStatus.CONSUMED
                || intent.getStatus() == IntentStatus.EXPIRED) {
            return; // already terminal — idempotent no-op
        }

        Optional<MatchProposal> pending = matchProposalRepository.findPendingByIntentId(intentId);
        if (pending.isPresent()) {
            cancelProposalAttempt(pending.get().getProposalId(), callerId);
            return;
        }

        // OPEN intent with no proposal — cancel it under the caller's calendar lock.
        userCalendarLockManager.lockUsers(List.of(callerId));
        WalkIntent locked = walkIntentRepository.findByIdForUpdate(intentId)
                .orElseThrow(() -> new DomainException(WalkIntentErrorCode.INTENT_NOT_FOUND));
        if (!locked.getUserId().equals(callerId)) {
            throw new DomainException(WalkIntentErrorCode.INTENT_NOT_OWNER);
        }
        if (locked.getStatus() == IntentStatus.MATCHING) {
            // Became MATCHING between discovery and lock — re-evaluate in a fresh attempt.
            throw new RetryableConcurrencyException("Intent became MATCHING during cancel");
        }
        if (locked.getStatus() == IntentStatus.CANCELLED
                || locked.getStatus() == IntentStatus.CONSUMED
                || locked.getStatus() == IntentStatus.EXPIRED) {
            return;
        }
        locked.cancel();
        walkIntentRepository.save(locked);
    }

    // ── List proposals ────────────────────────────────────────────────────────

    public List<MatchProposal> getPendingProposals(String userId) {
        return matchProposalRepository.findPendingForUser(userId);
    }

    // ── Scheduler sweep ───────────────────────────────────────────────────────

    /**
     * Expires overdue proposals and transitions both intents according to their flow type.
     * Each proposal is processed in its own transaction with a fresh locked read.
     */
    public void sweepExpiredProposals() {
        List<String> overdueIds = matchProposalRepository.findExpiredPendingIds();
        for (String proposalId : overdueIds) {
            try {
                matchingTransactionExecutor.execute(() -> {
                    expireProposalAttempt(proposalId);
                    return null;
                });
            } catch (Exception e) {
                log.warn("sweepExpiredProposals: failed to expire proposal={} error={}",
                        proposalId, e.getMessage());
            }
        }
    }

    private void expireProposalAttempt(String proposalId) {
        MatchProposal snapshot = matchProposalRepository.findById(proposalId).orElse(null);
        if (snapshot == null) {
            return;
        }

        userCalendarLockManager.lockUsers(List.of(snapshot.getUserIdA(), snapshot.getUserIdB()));

        WalkIntent[] pair = lockIntentsSorted(snapshot.getIntentIdA(), snapshot.getIntentIdB());

        MatchProposal proposal = matchProposalRepository.findByIdForUpdate(proposalId).orElse(null);
        if (proposal == null || proposal.getStatus() != ProposalStatus.PENDING) {
            return; // already closed or confirmed by another writer
        }
        if (!proposal.getExpiresAt().isBefore(Instant.now())) {
            return; // clock skew — not actually expired
        }

        proposal.expire();
        matchProposalRepository.save(proposal);
        transitionExpiredProposalIntents(pair[0], pair[1]);
    }

    // ── Proposal flow type helpers ────────────────────────────────────────────

    private enum ProposalFlowType { PUBLIC, PRIVATE }

    private ProposalFlowType resolveProposalFlowType(WalkIntent intentA, WalkIntent intentB) {
        boolean aPrivate = intentA.isPrivate();
        boolean bPrivate = intentB.isPrivate();
        if (aPrivate == bPrivate) {
            return aPrivate ? ProposalFlowType.PRIVATE : ProposalFlowType.PUBLIC;
        }
        log.error("Invariant violation: mismatched isPrivate flags — intentA={} isPrivate={}, intentB={} isPrivate={}",
                intentA.getId(), aPrivate, intentB.getId(), bPrivate);
        throw new IllegalStateException(
                "Proposal intent pair has mismatched isPrivate flags: intentA=" + intentA.getId()
                + " intentB=" + intentB.getId());
    }

    private void transitionRejectedProposalIntents(
            MatchProposal proposal, WalkIntent intentA, WalkIntent intentB, String callerId) {
        ProposalFlowType flowType = resolveProposalFlowType(intentA, intentB);
        if (flowType == ProposalFlowType.PRIVATE) {
            intentA.cancel();
            intentB.cancel();
        } else {
            intentA.unlock();
            intentB.unlock();
            String callerIntentId = proposal.resolveIntentIdForUser(callerId);
            String partnerUserId = callerIntentId.equals(proposal.getIntentIdA())
                    ? proposal.getUserIdB() : proposal.getUserIdA();
            WalkIntent callerIntent = callerIntentId.equals(proposal.getIntentIdA()) ? intentA : intentB;
            callerIntent.excludeUser(UUID.fromString(partnerUserId));
        }
        walkIntentRepository.save(intentA);
        walkIntentRepository.save(intentB);
    }

    private void transitionExpiredProposalIntents(WalkIntent intentA, WalkIntent intentB) {
        ProposalFlowType flowType = resolveProposalFlowType(intentA, intentB);
        if (flowType == ProposalFlowType.PRIVATE) {
            if (intentA.getStatus() == IntentStatus.MATCHING) { intentA.cancel(); walkIntentRepository.save(intentA); }
            if (intentB.getStatus() == IntentStatus.MATCHING) { intentB.cancel(); walkIntentRepository.save(intentB); }
        } else {
            if (intentA.getStatus() == IntentStatus.MATCHING) { intentA.unlock(); walkIntentRepository.save(intentA); }
            if (intentB.getStatus() == IntentStatus.MATCHING) { intentB.unlock(); walkIntentRepository.save(intentB); }
        }
    }

    // ── Intent expiry (scheduler) ─────────────────────────────────────────────

    /**
     * Expires an OPEN/MATCHING intent whose walk window is starting soon or has already
     * passed, cascading to any PENDING proposal so the partner is never left stranded.
     * Triggering intent → EXPIRED; partner → OPEN (public) / CANCELLED (private).
     */
    public void expireIntent(String intentId) {
        matchingTransactionExecutor.execute(() -> {
            expireIntentAttempt(intentId);
            return null;
        });
    }

    private void expireIntentAttempt(String intentId) {
        Instant now = Instant.now();

        // Discovery (non-locking).
        WalkIntent snapshot = walkIntentRepository.findById(intentId).orElse(null);
        if (snapshot == null) {
            return;
        }
        if (snapshot.getStatus() != IntentStatus.OPEN && snapshot.getStatus() != IntentStatus.MATCHING) {
            return;
        }

        Optional<MatchProposal> pending = matchProposalRepository.findPendingByIntentId(intentId);
        String partnerUserId = pending
                .map(p -> p.getIntentIdA().equals(intentId) ? p.getUserIdB() : p.getUserIdA())
                .orElse(null);

        // Lock users (caller + partner) in deterministic order before any row lock.
        List<String> users = new ArrayList<>();
        users.add(snapshot.getUserId());
        if (partnerUserId != null) {
            users.add(partnerUserId);
        }
        userCalendarLockManager.lockUsers(users);

        if (pending.isPresent()) {
            MatchProposal p = pending.get();
            WalkIntent[] pair = lockIntentsSorted(p.getIntentIdA(), p.getIntentIdB());
            MatchProposal proposal = matchProposalRepository.findByIdForUpdate(p.getProposalId()).orElse(null);
            if (proposal == null || proposal.getStatus() != ProposalStatus.PENDING) {
                return;
            }

            WalkIntent trigger = pair[0].getId().equals(intentId) ? pair[0] : pair[1];
            if (trigger.getStatus() != IntentStatus.OPEN && trigger.getStatus() != IntentStatus.MATCHING) {
                return;
            }
            if (!expiryDue(trigger, now)) {
                return;
            }

            proposal.expire();
            matchProposalRepository.save(proposal);

            trigger.expire();
            walkIntentRepository.save(trigger);

            WalkIntent partner = pair[0].getId().equals(intentId) ? pair[1] : pair[0];
            if (partner.getStatus() == IntentStatus.MATCHING) {
                if (partner.isPrivate()) {
                    partner.cancel();
                } else {
                    partner.unlock();
                }
                walkIntentRepository.save(partner);
            }
        } else {
            WalkIntent intent = walkIntentRepository.findByIdForUpdate(intentId).orElse(null);
            if (intent == null) {
                return;
            }
            if (intent.getStatus() != IntentStatus.OPEN && intent.getStatus() != IntentStatus.MATCHING) {
                return;
            }
            if (!expiryDue(intent, now)) {
                return;
            }
            intent.expire();
            walkIntentRepository.save(intent);
        }
    }

    private boolean expiryDue(WalkIntent intent, Instant now) {
        return !intent.getTimeWindowStart()
                .isAfter(now.plus(INTENT_EXPIRY_BUFFER_MINUTES, ChronoUnit.MINUTES));
    }

    private boolean isExcluded(WalkIntent intent, String otherUserId) {
        return intent.getExcludedUserIds().contains(UUID.fromString(otherUserId));
    }

    /**
     * Locks two intents via {@code SELECT ... FOR UPDATE} in deterministic lexicographic
     * order and returns them in {@code (intentIdA, intentIdB)} order.
     */
    private WalkIntent[] lockIntentsSorted(String intentIdA, String intentIdB) {
        String firstId = intentIdA.compareTo(intentIdB) <= 0 ? intentIdA : intentIdB;
        String secondId = firstId.equals(intentIdA) ? intentIdB : intentIdA;

        WalkIntent first = walkIntentRepository.findByIdForUpdate(firstId)
                .orElseThrow(() -> new DomainException(WalkIntentErrorCode.INTENT_NOT_FOUND));
        WalkIntent second = walkIntentRepository.findByIdForUpdate(secondId)
                .orElseThrow(() -> new DomainException(WalkIntentErrorCode.INTENT_NOT_FOUND));

        WalkIntent a = first.getId().equals(intentIdA) ? first : second;
        WalkIntent b = first.getId().equals(intentIdA) ? second : first;
        return new WalkIntent[]{a, b};
    }
}
