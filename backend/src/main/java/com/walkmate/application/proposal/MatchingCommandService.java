package com.walkmate.application.proposal;

import com.walkmate.application.walkintent.MatchingStrategy;
import com.walkmate.application.walkintent.MatchResult;
import com.walkmate.domain.hotspot.Hotspot;
import com.walkmate.domain.hotspot.HotspotErrorCode;
import com.walkmate.domain.hotspot.HotspotRepository;
import com.walkmate.domain.proposal.MatchProposal;
import com.walkmate.domain.proposal.MatchProposalRepository;
import com.walkmate.domain.proposal.ProposalErrorCode;
import com.walkmate.domain.session.WalkSession;
import com.walkmate.domain.session.WalkSessionRepository;
import com.walkmate.domain.shared.exception.DomainException;
import com.walkmate.domain.walkintent.IntentStatus;
import com.walkmate.domain.walkintent.WalkIntent;
import com.walkmate.domain.walkintent.WalkIntentErrorCode;
import com.walkmate.domain.walkintent.WalkIntentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MatchingCommandService {

    private static final long PROPOSAL_TTL_MINUTES = 30;

    private final WalkIntentRepository    walkIntentRepository;
    private final MatchProposalRepository matchProposalRepository;
    private final WalkSessionRepository   walkSessionRepository;
    private final HotspotRepository       hotspotRepository;
    private final MatchingStrategy        matchingStrategy;

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

        return Optional.of(matchProposalRepository.save(proposal));
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

        // Re-verify both are still OPEN under the lock
        if (first.getStatus() != IntentStatus.OPEN || second.getStatus() != IntentStatus.OPEN) {
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
        // Both intents remain OPEN — users can find new matches
    }

    // ── List proposals ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<MatchProposal> getPendingProposals(String userId) {
        return matchProposalRepository.findPendingForUser(userId);
    }
}
