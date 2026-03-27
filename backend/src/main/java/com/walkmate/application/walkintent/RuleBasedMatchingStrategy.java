package com.walkmate.application.walkintent;

import com.walkmate.domain.walkintent.WalkIntent;
import com.walkmate.domain.walkintent.WalkIntentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * MVP rule-based implementation of MatchingStrategy.
 *
 * Active scoring signal:
 *   - Overlap duration: longer shared window → higher base score.
 *
 * Signals stubbed for AI upgrade (add @param when implementing):
 *   - Shared interest tags    (+10/tag, max +20) — needs tags in walk_intent
 *   - Following each other    (+50)              — needs follow_relation table
 *   - TrustScore bonus                           — needs trust_score table
 *   - No-show penalty                            — needs walk_session outcome data
 *
 * To activate AI matching: create AiMatchingStrategy @Primary — this class
 * becomes the automatic fallback (no service or controller changes needed).
 */
@Primary
@Component
@RequiredArgsConstructor
public class RuleBasedMatchingStrategy implements MatchingStrategy {

    private static final Duration MIN_WALK_DURATION = WalkIntent.MIN_WALK_DURATION;

    // ── Scoring weights ───────────────────────────────────────────────────
    private static final int WEIGHT_OVERLAP_PER_MINUTE = 1;
    // TODO (AI Upgrade — Tags):    private static final int WEIGHT_SHARED_TAG  = 10;
    // TODO (AI Upgrade — Social):  private static final int WEIGHT_FOLLOWING   = 50;
    // TODO (AI Upgrade — Trust):   private static final int WEIGHT_TRUST_POINT = 2;
    // TODO (AI Upgrade — NoShow):  private static final int PENALTY_NOSHOW     = -30;

    private final WalkIntentRepository walkIntentRepository;

    // ─────────────────────────────────────────────────────────────────────
    // Stage 1: DB-level hard filter
    // ─────────────────────────────────────────────────────────────────────

    @Override
    public List<WalkIntent> findCandidates(WalkIntent intent) {
        return walkIntentRepository.findOpenCandidates(
                intent.getHotspotId(),
                intent.getTimeWindowStart(),
                intent.getTimeWindowEnd(),
                intent.getMatchingConstraints().ageMin(),
                intent.getMatchingConstraints().ageMax(),
                intent.getUserId(),
                MIN_WALK_DURATION
        );
    }

    // ─────────────────────────────────────────────────────────────────────
    // Stage 2: In-memory scoring
    // ─────────────────────────────────────────────────────────────────────

    @Override
    public Optional<MatchResult> match(WalkIntent intent, List<WalkIntent> candidates) {
        return candidates.stream()
                .map(candidate -> buildResult(intent, candidate))
                .max(Comparator.comparingInt(MatchResult::score));
    }

    private MatchResult buildResult(WalkIntent a, WalkIntent b) {
        Instant overlapStart = a.getTimeWindowStart().isAfter(b.getTimeWindowStart())
                ? a.getTimeWindowStart() : b.getTimeWindowStart();
        Instant overlapEnd = a.getTimeWindowEnd().isBefore(b.getTimeWindowEnd())
                ? a.getTimeWindowEnd() : b.getTimeWindowEnd();

        int totalScore = scoreOverlapDuration(overlapStart, overlapEnd);
        // TODO (AI Upgrade — Tags):   totalScore += scoreSharedTags(a, b);
        // TODO (AI Upgrade — Social): totalScore += scoreFollowRelationship(a.getUserId(), b.getUserId());
        // TODO (AI Upgrade — Trust):  totalScore += scoreTrustLevel(b.getUserId());
        // TODO (AI Upgrade — NoShow): totalScore += penalizeNoShowHistory(b.getUserId());

        return new MatchResult(b, overlapStart, overlapEnd, totalScore);
    }

    // ── Individual scoring methods ────────────────────────────────────────

    private int scoreOverlapDuration(Instant overlapStart, Instant overlapEnd) {
        long overlapMinutes = Duration.between(overlapStart, overlapEnd).toMinutes();
        return (int) (overlapMinutes * WEIGHT_OVERLAP_PER_MINUTE);
    }

    // TODO (AI Upgrade — Tags):   private int scoreSharedTags(WalkIntent a, WalkIntent b) { ... }
    // TODO (AI Upgrade — Social): private int scoreFollowRelationship(String userIdA, String userIdB) { ... }
    // TODO (AI Upgrade — Trust):  private int scoreTrustLevel(String userId) { ... }
    // TODO (AI Upgrade — NoShow): private int penalizeNoShowHistory(String userId) { ... }
}
