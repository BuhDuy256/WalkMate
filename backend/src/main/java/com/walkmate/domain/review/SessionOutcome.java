package com.walkmate.domain.review;

/**
 * Maps a terminal session outcome to its system-driven trust-score delta (Stage 1).
 *
 * <p>Stage 2 (review-driven) adjustments are handled separately by
 * {@link com.walkmate.application.review.ReviewCommandService} using a star-rating curve
 * and do not go through this enum.
 *
 * <p>Cancellation is time-gated:
 * <ul>
 *   <li>{@link #CANCELLED_LATE}  — cancelled within 2 hours of scheduled start → -20</li>
 *   <li>{@link #CANCELLED_EARLY} — cancelled 2+ hours before scheduled start   →   0</li>
 * </ul>
 */
public enum SessionOutcome {

    COMPLETED(+5),
    NO_SHOW(-100),
    CANCELLED_LATE(-20),
    CANCELLED_EARLY(0);

    private final int delta;

    SessionOutcome(int delta) {
        this.delta = delta;
    }

    public int getDelta() { return delta; }
}
