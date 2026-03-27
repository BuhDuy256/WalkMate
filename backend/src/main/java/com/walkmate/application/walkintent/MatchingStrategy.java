package com.walkmate.application.walkintent;

import com.walkmate.domain.walkintent.WalkIntent;

import java.util.List;
import java.util.Optional;

/**
 * Strategy contract for the two-stage intent matching pipeline.
 *
 * Stage 1 — findCandidates: pushes hard filters down to the database.
 *   Only intents that satisfy geography, time overlap, and age-range
 *   constraints reach the application layer.
 *
 * Stage 2 — match: applies scoring logic in-memory over the small
 *   candidate set and picks the best fit.
 *
 * Swap implementations via Spring @Primary to upgrade from rule-based
 * MVP to AI-powered matching without touching service or controller code.
 */
public interface MatchingStrategy {

    /**
     * Stage 1: DB-level hard filter. Returns only valid candidates.
     */
    List<WalkIntent> findCandidates(WalkIntent intent);

    /**
     * Stage 2: In-memory scoring and selection.
     * Returns empty if no suitable candidate exists (still searching).
     */
    Optional<MatchResult> match(WalkIntent intent, List<WalkIntent> candidates);
}
