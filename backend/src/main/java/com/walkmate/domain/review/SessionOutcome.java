package com.walkmate.domain.review;

/**
 * Maps a terminal session status to its trust-score delta.
 *
 * Used by {@link TrustScorePolicy} to calculate the reviewee's new score.
 */
public enum SessionOutcome {

    COMPLETED(+5),
    NO_SHOW(-20),
    ABORTED(-10),
    CANCELLED(-5);

    private final int delta;

    SessionOutcome(int delta) {
        this.delta = delta;
    }

    public int getDelta() { return delta; }
}
