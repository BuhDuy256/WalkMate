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
     * Re-normalises so that {@code weightTimeOverlap + weightInterest + weightBehavior = 1.0}.
     * If all three are zero (degenerate case), resets to equal thirds.
     */
    public void normalize() {
        double total = weightTimeOverlap + weightInterest + weightBehavior;
        if (total <= 0) {
            weightTimeOverlap = DEFAULT_WEIGHT;
            weightInterest    = DEFAULT_WEIGHT;
            weightBehavior    = DEFAULT_WEIGHT;
        } else {
            weightTimeOverlap = weightTimeOverlap / total;
            weightInterest    = weightInterest    / total;
            weightBehavior    = weightBehavior    / total;
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
