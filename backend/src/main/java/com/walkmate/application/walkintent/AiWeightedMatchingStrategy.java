package com.walkmate.application.walkintent;

import com.walkmate.domain.social.SocialRepository;
import com.walkmate.domain.user.UserProfileRepository;
import com.walkmate.domain.user.UserRepository;
import com.walkmate.domain.walkintent.MatchingPreference;
import com.walkmate.domain.walkintent.MatchingPreferenceRepository;
import com.walkmate.domain.walkintent.WalkIntent;
import com.walkmate.domain.walkintent.WalkIntentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * AI-weighted matching strategy that replaces the rule-based MVP.
 *
 * <p>For each candidate, computes a weighted score:</p>
 * <pre>
 *   TotalScore = W_time     × S_time
 *              + W_interest × S_tags
 *              + W_behavior × S_trust
 * </pre>
 * <ul>
 *   <li>{@code S_time}  = min(overlapMinutes / 60 × 100, 100)   → [0, 100]</li>
 *   <li>{@code S_tags}  = Jaccard(profileTagsA, profileTagsB) × 100 → [0, 100] (50 when both empty)</li>
 *   <li>{@code S_trust} = candidateTrustScore / 10              → [0, 100] for scores [0, 1000]</li>
 * </ul>
 * <p>Weights are personalised per user via {@link MatchingPreference} and default to equal thirds
 * (0.333…) for users who have never submitted a review with tags.</p>
 */
@Primary
@Component
@RequiredArgsConstructor
public class AiWeightedMatchingStrategy implements MatchingStrategy {

    private static final Duration MIN_WALK_DURATION = WalkIntent.MIN_WALK_DURATION;

    private final WalkIntentRepository         walkIntentRepository;
    private final SocialRepository             socialRepository;
    private final UserRepository               userRepository;
    private final UserProfileRepository        userProfileRepository;
    private final MatchingPreferenceRepository matchingPreferenceRepository;

    // ─────────────────────────────────────────────────────────────────────
    // Stage 1: DB-level hard filter (identical to RuleBasedMatchingStrategy)
    // ─────────────────────────────────────────────────────────────────────

    @Override
    public List<WalkIntent> findCandidates(WalkIntent intent) {
        List<WalkIntent> candidates = walkIntentRepository.findOpenCandidates(
                intent.getHotspotId(),
                intent.getTimeWindowStart(),
                intent.getTimeWindowEnd(),
                intent.getMatchingConstraints().ageMin(),
                intent.getMatchingConstraints().ageMax(),
                intent.getUserId(),
                MIN_WALK_DURATION
        );

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
    // Stage 2: AI-weighted in-memory scoring
    // ─────────────────────────────────────────────────────────────────────

    @Override
    public Optional<MatchResult> match(WalkIntent intent, List<WalkIntent> candidates) {
        UUID callerUuid = UUID.fromString(intent.getUserId());

        MatchingPreference pref = matchingPreferenceRepository.findByUserId(callerUuid)
                .orElseGet(() -> MatchingPreference.defaultFor(callerUuid));

        List<String> callerTags = userProfileRepository.findTagsByUserId(callerUuid);

        return candidates.stream()
                .map(candidate -> scoreCandidate(intent, candidate, pref, callerTags))
                .max(Comparator.comparingDouble(ScoredResult::score))
                .map(ScoredResult::toMatchResult);
    }

    // ── Scoring ───────────────────────────────────────────────────────────────

    private ScoredResult scoreCandidate(WalkIntent a, WalkIntent b,
                                         MatchingPreference pref, List<String> tagsA) {
        Instant overlapStart = a.getTimeWindowStart().isAfter(b.getTimeWindowStart())
                ? a.getTimeWindowStart() : b.getTimeWindowStart();
        Instant overlapEnd = a.getTimeWindowEnd().isBefore(b.getTimeWindowEnd())
                ? a.getTimeWindowEnd() : b.getTimeWindowEnd();

        double sTime  = scoreTime(overlapStart, overlapEnd);
        double sTags  = scoreTags(tagsA, b);
        double sTrust = scoreTrust(b.getUserId());

        double total = (pref.getWeightTimeOverlap() * sTime)
                     + (pref.getWeightInterest()    * sTags)
                     + (pref.getWeightBehavior()    * sTrust);

        return new ScoredResult(b, overlapStart, overlapEnd, total);
    }

    private double scoreTime(Instant start, Instant end) {
        long overlapMinutes = Duration.between(start, end).toMinutes();
        return Math.min((overlapMinutes / 60.0) * 100.0, 100.0);
    }

    private double scoreTags(List<String> tagsA, WalkIntent b) {
        UUID candidateUuid = UUID.fromString(b.getUserId());
        List<String> tagsB = userProfileRepository.findTagsByUserId(candidateUuid);

        if (tagsA.isEmpty() && tagsB.isEmpty()) return 50.0;

        Set<String> setA = toLower(tagsA);
        Set<String> setB = toLower(tagsB);

        Set<String> intersection = new HashSet<>(setA);
        intersection.retainAll(setB);

        Set<String> union = new HashSet<>(setA);
        union.addAll(setB);

        return union.isEmpty() ? 50.0 : (intersection.size() / (double) union.size()) * 100.0;
    }

    private double scoreTrust(String userId) {
        return userRepository.findById(userId)
                .map(u -> u.getTrustScore() / 10.0)
                .orElse(0.0);
    }

    private Set<String> toLower(List<String> tags) {
        Set<String> result = new HashSet<>();
        for (String tag : tags) result.add(tag.toLowerCase());
        return result;
    }

    // ── Internal result carrier ───────────────────────────────────────────────

    private record ScoredResult(WalkIntent matched,
                                 Instant overlapStart,
                                 Instant overlapEnd,
                                 double score) {
        MatchResult toMatchResult() {
            return new MatchResult(matched, overlapStart, overlapEnd, (int) score);
        }
    }
}
