package com.walkmate.application.walkintent;

import com.walkmate.domain.review.ReviewTag;
import com.walkmate.domain.walkintent.MatchingPreference;
import com.walkmate.domain.walkintent.MatchingPreferenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Adjusts a reviewer's AI matching weights based on the structured tags they chose.
 *
 * <p>INTEREST tags (POSITIVE_INTEREST / NEGATIVE_INTEREST) increment {@code weightInterest};
 * BEHAVIOR tags (POSITIVE_BEHAVIOR / NEGATIVE_BEHAVIOR) increment {@code weightBehavior}.
 * After all increments the three weights are re-normalised to sum to 1.0.</p>
 *
 * <p>All training runs asynchronously (via {@code @Async}) so it never blocks the HTTP
 * response.  Failures are caught and logged rather than propagated — a failed training
 * update is non-critical; the triggering action is already committed.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiTrainingService {

    private final MatchingPreferenceRepository matchingPreferenceRepository;

    @Async
    public void trainWeightsFromReview(UUID reviewerId, List<ReviewTag> selectedTags) {
        if (selectedTags == null || selectedTags.isEmpty()) return;

        try {
            MatchingPreference pref = matchingPreferenceRepository.findByUserId(reviewerId)
                    .orElseGet(() -> MatchingPreference.defaultFor(reviewerId));

            for (ReviewTag tag : selectedTags) {
                String type = tag.tagType();
                if (type == null) continue;
                if (type.contains("INTEREST")) {
                    pref.adjustWeightInterest(0.05);
                } else if (type.contains("BEHAVIOR")) {
                    pref.adjustWeightBehavior(0.05);
                }
            }

            pref.normalize();
            pref.updateLastTrainedAt(Instant.now());
            matchingPreferenceRepository.save(pref);

            log.debug("AI weights updated: reviewer={} timeOverlap={:.3f} interest={:.3f} behavior={:.3f}",
                    reviewerId,
                    pref.getWeightTimeOverlap(),
                    pref.getWeightInterest(),
                    pref.getWeightBehavior());
        } catch (Exception e) {
            log.warn("AI weight training failed for reviewer={}: {}", reviewerId, e.getMessage());
        }
    }

    /**
     * Adjusts the reporter's AI matching weights after they submit an incident report.
     *
     * <p>Filing a report is a strong signal that behavioral trustworthiness matters to this
     * user.  {@code weightBehavior} is incremented by a base of +0.10, with an additional
     * +0.05 for high-severity reasons (SAFETY_CONCERN, PARTNER_MISCONDUCT), for a maximum
     * increment of +0.15 per report.  The two-pass cap in {@code normalize()} ensures
     * {@code weightBehavior} cannot exceed {@link MatchingPreference#MAX_WEIGHT_CAP}.</p>
     *
     * <p>Runs {@code @Async} — failures are logged, not propagated.</p>
     */
    @Async
    public void trainWeightsFromReport(UUID reporterId, String reason) {
        try {
            MatchingPreference pref = matchingPreferenceRepository.findByUserId(reporterId)
                    .orElseGet(() -> MatchingPreference.defaultFor(reporterId));

            // Base increment for any report
            pref.adjustWeightBehavior(0.10);

            // Severity bonus for high-signal reasons
            if ("SAFETY_CONCERN".equals(reason) || "PARTNER_MISCONDUCT".equals(reason)) {
                pref.adjustWeightBehavior(0.05);
            }

            pref.normalize();
            pref.updateLastTrainedAt(Instant.now());
            matchingPreferenceRepository.save(pref);

            log.debug("AI weights updated from report: reporter={} timeOverlap={:.3f} interest={:.3f} behavior={:.3f}",
                    reporterId,
                    pref.getWeightTimeOverlap(),
                    pref.getWeightInterest(),
                    pref.getWeightBehavior());
        } catch (Exception e) {
            log.warn("AI weight training (report) failed for reporter={}: {}", reporterId, e.getMessage());
        }
    }
}
