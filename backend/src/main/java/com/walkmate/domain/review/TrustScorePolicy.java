package com.walkmate.domain.review;

/**
 * Domain policy for trust-score adjustments.
 *
 * <p>All callers MUST use {@link #apply(int, SessionOutcome)} instead of
 * applying deltas manually, so the bounding rule is enforced in one place.</p>
 */
public final class TrustScorePolicy {

    public static final int MIN_SCORE = 0;
    public static final int MAX_SCORE = 1000;

    private TrustScorePolicy() {}

    /**
     * Returns the new trust score after applying the outcome's delta,
     * clamped to [{@value #MIN_SCORE}, {@value #MAX_SCORE}].
     *
     * @param currentScore the reviewee's current trust score
     * @param outcome      the session outcome driving the adjustment (Stage 1)
     * @return new bounded score
     */
    public static int apply(int currentScore, SessionOutcome outcome) {
        return apply(currentScore, outcome.getDelta());
    }

    /**
     * Returns the new trust score after applying an arbitrary raw delta,
     * clamped to [{@value #MIN_SCORE}, {@value #MAX_SCORE}].
     *
     * <p>Use this overload for Stage 2 (review-driven) adjustments where the
     * delta is computed from a star-rating curve rather than a {@link SessionOutcome}.
     *
     * @param currentScore the reviewee's current trust score
     * @param delta        raw signed adjustment (positive or negative)
     * @return new bounded score
     */
    public static int apply(int currentScore, int delta) {
        return Math.max(MIN_SCORE, Math.min(MAX_SCORE, currentScore + delta));
    }
}
