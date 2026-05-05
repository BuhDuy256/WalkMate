package com.walkmate.domain.review;

/**
 * Domain policy for trust-score adjustments.
 *
 * <p>
 * All callers MUST use {@link #apply(int, SessionOutcome)} instead of
 * applying deltas manually, so the bounding rule is enforced in one place.
 * </p>
 */
public final class TrustScorePolicy {

    public static final int MIN_SCORE = 0;
    public static final int MAX_SCORE = 1000;

    private TrustScorePolicy() {
    }

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
     * <p>
     * Use this overload for Stage 2 (review-driven) adjustments where the
     * delta is computed from a star-rating curve rather than a
     * {@link SessionOutcome}.
     *
     * @param currentScore the reviewee's current trust score
     * @param delta        raw signed adjustment (positive or negative)
     * @return new bounded score
     */
    public static int apply(int currentScore, int delta) {
        return Math.max(MIN_SCORE, Math.min(MAX_SCORE, currentScore + delta));
    }

    /**
     * Returns the trust-score delta for a submitted incident report reason (Stage
     * 3).
     *
     * <p>
     * Penalties are one-directional (negative or zero). The result is intended to
     * be passed directly to {@link #apply(int, int)}.
     *
     * @param reason the raw reason string from the report request
     * @return negative delta, or 0 for ambiguous/unrecognised reasons
     */
    public static int deltaForReason(String reason) {
        if (reason == null)
            return 0;
        return switch (reason) {
            case "SAFETY_CONCERN" -> -50;
            case "PARTNER_MISCONDUCT" -> -30;
            case "OTHER" -> -10;
            default -> 0; // EMERGENCY and unknown values → no penalty
        };
    }
}
