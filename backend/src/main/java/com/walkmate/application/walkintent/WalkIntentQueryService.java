package com.walkmate.application.walkintent;

import com.walkmate.domain.shared.exception.DomainException;
import com.walkmate.domain.walkintent.WalkIntent;
import com.walkmate.domain.walkintent.WalkIntentErrorCode;
import com.walkmate.domain.walkintent.WalkIntentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class WalkIntentQueryService {

    private final WalkIntentRepository walkIntentRepository;
    private final MatchingStrategy matchingStrategy;

    /**
     * Polls for the best match for a given intent.
     *
     * Returns empty if the intent is still PENDING and no suitable candidate
     * exists yet. The frontend polls this endpoint periodically.
     *
     * Returns the MatchResult (containing the matched WalkIntent and overlap
     * window) when a candidate is found.
     */
    @Transactional(readOnly = true)
    public Optional<MatchResult> findMatch(String intentId) {
        // 1. Load and validate the intent exists
        WalkIntent intent = walkIntentRepository.findById(intentId)
                .orElseThrow(() -> new DomainException(WalkIntentErrorCode.INTENT_NOT_FOUND));

        // 2. Stage 1: DB-level hard filter (same hotspot, valid time overlap, age range)
        List<WalkIntent> candidates = matchingStrategy.findCandidates(intent);

        // 3. Stage 2: In-memory scoring — picks the highest-scoring candidate
        return matchingStrategy.match(intent, candidates);
    }
}
