package com.walkmate.domain.walkintent;

import java.time.Instant;
import java.util.UUID;

/**
 * Per-user weights used by {@link com.walkmate.application.walkintent.AiWeightedMatchingStrategy}.
 *
 * <p>The three weights are normalised to sum to 1.0 so the formula
 * {@code W_time·S_time + W_interest·S_tags + W_behavior·S_trust} stays in the [0, 100] range.
 * Weights are adjusted by {@link com.walkmate.application.walkintent.AiTrainingService} after
 * each review and then re-normalised.</p>
 *
 * Maps to the {@code matching_preference_model} table.
 */
public class MatchingPreference {

    private static final double DEFAULT_WEIGHT = 1.0 / 3.0;

    /**
     * Hard cap applied after normalization.
     * No single weight may exceed 70 % of the total score, ensuring the
     * other two factors always retain at least 30 % combined influence.
     */
    public static final double MAX_WEIGHT_CAP = 0.70;

    private final UUID    userId;
    private double        weightTimeOverlap;
    private double        weightInterest;
    private double        weightBehavior;
    private Instant       lastTrainedAt;

    /** Rehydration constructor — called by the repository when loading from DB. */
    public MatchingPreference(UUID userId,
                              double weightTimeOverlap,
                              double weightInterest,
                              double weightBehavior,
                              Instant lastTrainedAt) {
        this.userId            = userId;
        this.weightTimeOverlap = weightTimeOverlap;
        this.weightInterest    = weightInterest;
        this.weightBehavior    = weightBehavior;
        this.lastTrainedAt     = lastTrainedAt;
    }

    /** Returns a new preference object with equal default weights (0.333…). */
    public static MatchingPreference defaultFor(UUID userId) {
        return new MatchingPreference(userId, DEFAULT_WEIGHT, DEFAULT_WEIGHT, DEFAULT_WEIGHT, Instant.now());
    }

    // ── Mutation ──────────────────────────────────────────────────────────────

    public void adjustWeightInterest(double delta) {
        weightInterest = Math.max(0.0, weightInterest + delta);
    }

    public void adjustWeightBehavior(double delta) {
        weightBehavior = Math.max(0.0, weightBehavior + delta);
    }

    /**
     * Re-normalises so that {@code weightTimeOverlap + weightInterest + weightBehavior = 1.0},
     * then enforces {@link #MAX_WEIGHT_CAP} so no single weight dominates the score.
     *
     * <p><b>Pass 1 — Standard normalization:</b> divide each weight by the sum.
     * Resets to equal thirds on degenerate zero-sum.
     *
     * <p><b>Pass 2 — Cap enforcement:</b> if any normalized weight exceeds
     * {@code MAX_WEIGHT_CAP}, clamp it and redistribute the remainder proportionally
     * to the other two. The invariant {@code sum = 1.0} is preserved after both passes.
     */
    public void normalize() {
        // Pass 1 — standard normalization
        double total = weightTimeOverlap + weightInterest + weightBehavior;
        if (total <= 0) {
            weightTimeOverlap = DEFAULT_WEIGHT;
            weightInterest    = DEFAULT_WEIGHT;
            weightBehavior    = DEFAULT_WEIGHT;
            return;
        }
        weightTimeOverlap = weightTimeOverlap / total;
        weightInterest    = weightInterest    / total;
        weightBehavior    = weightBehavior    / total;

        // Pass 2 — cap enforcement (at most one weight can exceed the cap at a time
        // because the three sum to 1.0 and MAX_WEIGHT_CAP >= 1/3)
        double[] weights = { weightTimeOverlap, weightInterest, weightBehavior };
        int cappedIdx = -1;
        for (int i = 0; i < weights.length; i++) {
            if (weights[i] > MAX_WEIGHT_CAP) {
                cappedIdx = i;
                break;
            }
        }

        if (cappedIdx >= 0) {
            weights[cappedIdx] = MAX_WEIGHT_CAP;
            double remainder = 1.0 - MAX_WEIGHT_CAP;
            double subSum = 0.0;
            for (int i = 0; i < weights.length; i++) {
                if (i != cappedIdx) subSum += weights[i];
            }
            for (int i = 0; i < weights.length; i++) {
                if (i != cappedIdx) {
                    weights[i] = (subSum > 0)
                            ? weights[i] / subSum * remainder
                            : remainder / 2.0;
                }
            }
            weightTimeOverlap = weights[0];
            weightInterest    = weights[1];
            weightBehavior    = weights[2];
        }
    }

    public void updateLastTrainedAt(Instant when) {
        this.lastTrainedAt = when;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public UUID    getUserId()           { return userId; }
    public double  getWeightTimeOverlap(){ return weightTimeOverlap; }
    public double  getWeightInterest()   { return weightInterest; }
    public double  getWeightBehavior()   { return weightBehavior; }
    public Instant getLastTrainedAt()    { return lastTrainedAt; }
}
