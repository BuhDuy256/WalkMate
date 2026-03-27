package com.walkmate.application.walkintent;

import com.walkmate.domain.walkintent.WalkIntent;

import java.time.Instant;

/**
 * The outcome of a successful match between two WalkIntents.
 *
 * @param matched      The best candidate intent found for the requesting user.
 * @param overlapStart The start of the shared walk window.
 * @param overlapEnd   The end of the shared walk window.
 * @param score        Total score from the MatchingStrategy. Higher = better fit.
 */
public record MatchResult(
        WalkIntent matched,
        Instant overlapStart,
        Instant overlapEnd,
        int score
) {
}
