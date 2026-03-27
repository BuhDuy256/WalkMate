package com.walkmate.domain.walkintent;

import com.walkmate.domain.shared.exception.DomainException;

/**
 * Value object holding all hard-filter criteria for matching.
 * Stored as JSONB in the DB — add new fields here when new filter
 * types are introduced without requiring a schema migration.
 *
 * Current fields:
 *   ageMin / ageMax — desired age range of the walking partner.
 *
 * Future fields (do not add until ready):
 *   genderPreference, purposeTags, etc.
 */
public record MatchingConstraints(int ageMin, int ageMax) {

    public MatchingConstraints {
        if (ageMin < 0) {
            throw new DomainException(WalkIntentErrorCode.INVALID_INTENT_DATA, "Age min must be >= 0");
        }
        if (ageMax < ageMin) {
            throw new DomainException(WalkIntentErrorCode.INVALID_AGE_RANGE);
        }
    }

    /** Convenience: default open range (no age preference). */
    public static MatchingConstraints open() {
        return new MatchingConstraints(0, 120);
    }
}
