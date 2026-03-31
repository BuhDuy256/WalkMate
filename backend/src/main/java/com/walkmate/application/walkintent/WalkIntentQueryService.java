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

    /** Returns all OPEN intents for the authenticated user. */
    @Transactional(readOnly = true)
    public List<WalkIntent> listActiveIntents(String userId) {
        return walkIntentRepository.findOpenByUserId(userId);
    }

    /**
     * Polls for the best match for a given intent.
     *
     * Returns empty if no suitable candidate exists yet.
     * Returns the MatchResult when a candidate is found.
     */
    @Transactional(readOnly = true)
    public Optional<MatchResult> findMatch(String intentId) {
        WalkIntent intent = walkIntentRepository.findById(intentId)
                .orElseThrow(() -> new DomainException(WalkIntentErrorCode.INTENT_NOT_FOUND));

        List<WalkIntent> candidates = matchingStrategy.findCandidates(intent);
        return matchingStrategy.match(intent, candidates);
    }
}
