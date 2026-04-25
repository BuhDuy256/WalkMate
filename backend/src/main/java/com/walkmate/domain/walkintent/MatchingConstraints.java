package com.walkmate.domain.walkintent;

import com.walkmate.domain.shared.exception.DomainException;

/**
 * Value object holding all hard-filter criteria for matching.
 * Stored as JSONB in the DB — add new fields here when new filter
 * types are introduced without requiring a schema migration.
 *
 * Fields:
 *   ageMin / ageMax       — desired age range of the walking partner.
 *   preferredGender       — "ANY", "MALE", or "FEMALE".
 */
public record MatchingConstraints(int ageMin, int ageMax, String preferredGender) {

    public MatchingConstraints {
        if (ageMin < 0) {
            throw new DomainException(WalkIntentErrorCode.INVALID_INTENT_DATA, "Age min must be >= 0");
        }
        if (ageMax < ageMin) {
            throw new DomainException(WalkIntentErrorCode.INVALID_AGE_RANGE);
        }
        if (preferredGender == null || preferredGender.isBlank()) {
            preferredGender = "ANY";
        }
    }

    /** Convenience: default open range (no age or gender preference). */
    public static MatchingConstraints open() {
        return new MatchingConstraints(0, 120, "ANY");
    }
}
