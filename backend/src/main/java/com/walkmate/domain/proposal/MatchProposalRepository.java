package com.walkmate.domain.proposal;

import java.util.List;
import java.util.Optional;

public interface MatchProposalRepository {

    MatchProposal save(MatchProposal proposal);

    Optional<MatchProposal> findById(String id);

    /**
     * Loads the proposal with a pessimistic write lock on the {@code match_proposal} row
     * only ({@code FOR UPDATE OF mp}). MUST be called inside an active transaction.
     * Joined rows (intents, hotspot) are read without being locked.
     */
    Optional<MatchProposal> findByIdForUpdate(String id);

    /** Returns an existing PENDING proposal that involves the given intent (either side). */
    Optional<MatchProposal> findPendingByIntentId(String intentId);

    /** Returns all PENDING proposals where either intent belongs to the given user. */
    List<MatchProposal> findPendingForUser(String userId);

    /** Returns all PENDING proposals whose expires_at is in the past. */
    List<MatchProposal> findExpiredPending();

    /**
     * Returns only the ids of PENDING proposals whose expires_at is in the past.
     * Used by the scheduler for cheap discovery before per-item locked processing.
     */
    List<String> findExpiredPendingIds();
}
