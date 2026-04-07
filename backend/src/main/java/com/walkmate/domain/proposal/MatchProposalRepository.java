package com.walkmate.domain.proposal;

import java.util.List;
import java.util.Optional;

public interface MatchProposalRepository {

    MatchProposal save(MatchProposal proposal);

    Optional<MatchProposal> findById(String id);

    /** Returns an existing PENDING proposal that involves the given intent (either side). */
    Optional<MatchProposal> findPendingByIntentId(String intentId);

    /** Returns all PENDING proposals where either intent belongs to the given user. */
    List<MatchProposal> findPendingForUser(String userId);

    /** Returns all PENDING proposals whose expires_at is in the past. */
    List<MatchProposal> findExpiredPending();
}
