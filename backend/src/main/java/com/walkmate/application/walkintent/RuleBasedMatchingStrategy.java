package com.walkmate.application.walkintent;

import com.walkmate.domain.social.SocialRepository;
import com.walkmate.domain.user.UserRepository;
import com.walkmate.domain.walkintent.WalkIntent;
import com.walkmate.domain.walkintent.WalkIntentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

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
@Component
@RequiredArgsConstructor
public class RuleBasedMatchingStrategy implements MatchingStrategy {

    private static final Duration MIN_WALK_DURATION = WalkIntent.MIN_WALK_DURATION;

    // TODO [DOMAIN_CONTRACTS §4.2 Invariant 3]: filter out PRIVATE users before returning candidates
    //      When the matching engine reads candidates, add: WHERE visibility_mode = 'PUBLIC'
    //      to the user_account join in WalkIntentJdbcRepository / candidate query.

    // ── Scoring weights ───────────────────────────────────────────────────
    private static final int WEIGHT_OVERLAP_PER_MINUTE = 1;
    // TODO (AI Upgrade — Tags):   private static final int WEIGHT_SHARED_TAG = 10;
    // TODO (AI Upgrade — Social): private static final int WEIGHT_FOLLOWING  = 50;

    /** Trust score is normalised to a 0–10 bonus: score / 100, range [0, 1000]. */
    private static final int TRUST_SCORE_DIVISOR = 100;

    private final WalkIntentRepository walkIntentRepository;
    private final SocialRepository     socialRepository;
    private final UserRepository       userRepository;

    // ─────────────────────────────────────────────────────────────────────
    // Stage 1: DB-level hard filter
    // ─────────────────────────────────────────────────────────────────────

    @Override
    public List<WalkIntent> findCandidates(WalkIntent intent) {
        // Stage 1: DB-level hard filter (hotspot, time window, age constraints)
        List<WalkIntent> candidates = walkIntentRepository.findOpenCandidates(
                intent.getHotspotId(),
                intent.getTimeWindowStart(),
                intent.getTimeWindowEnd(),
                intent.getMatchingConstraints().ageMin(),
                intent.getMatchingConstraints().ageMax(),
                intent.getUserId(),
                MIN_WALK_DURATION
        );

        // Stage 1b: Block filter — load the caller's full exclusion set in ONE
        // DB round-trip (UNION query), then discard in-memory.  This keeps the
        // hot path at O(1) DB queries regardless of candidate list size.
        // See OPTIMIZATION_DECISION_LOG.md for the full rationale.
        UUID callerId = UUID.fromString(intent.getUserId());
        Set<UUID> blocked = socialRepository.getBlockedAndBlockerIds(callerId);
        if (!blocked.isEmpty()) {
            candidates = candidates.stream()
                    .filter(c -> !blocked.contains(UUID.fromString(c.getUserId())))
                    .toList();
        }

        return candidates;
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
        totalScore += scoreTrustLevel(b.getUserId());
        // TODO (AI Upgrade — Tags):   totalScore += scoreSharedTags(a, b);
        // TODO (AI Upgrade — Social): totalScore += scoreFollowRelationship(a.getUserId(), b.getUserId());

        return new MatchResult(b, overlapStart, overlapEnd, totalScore);
    }

    // ── Individual scoring methods ────────────────────────────────────────

    private int scoreOverlapDuration(Instant overlapStart, Instant overlapEnd) {
        long overlapMinutes = Duration.between(overlapStart, overlapEnd).toMinutes();
        return (int) (overlapMinutes * WEIGHT_OVERLAP_PER_MINUTE);
    }

    /**
     * Returns a proportional trust bonus for a candidate.
     *
     * Formula: {@code trustScore / TRUST_SCORE_DIVISOR} → 0–10 range for scores 0–1000.
     * A candidate at the default baseline (500) contributes +5; a perfect-trust user
     * contributes +10; a zero-trust user contributes 0.
     *
     * Missing users (deleted accounts) return 0 rather than failing the match.
     */
    private int scoreTrustLevel(String userId) {
        return userRepository.findById(userId)
                .map(u -> u.getTrustScore() / TRUST_SCORE_DIVISOR)
                .orElse(0);
    }

    // TODO (AI Upgrade — Tags):   private int scoreSharedTags(WalkIntent a, WalkIntent b) { ... }
    // TODO (AI Upgrade — Social): private int scoreFollowRelationship(String userIdA, String userIdB) { ... }
}
