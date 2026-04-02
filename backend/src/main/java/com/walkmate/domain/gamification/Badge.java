package com.walkmate.domain.gamification;

/**
 * Represents an achievable badge. Each value maps to a milestone checked
 * by {@link BadgePolicy#evaluateEarned(UserStats, java.util.Set)}.
 */
public enum Badge {

    /** Awarded on the user's first completed walk session. */
    FIRST_WALK,

    /** Awarded after completing 5 walk sessions. */
    FIRST_FIVE,

    /** Awarded after completing 10 walk sessions. */
    CENTURY_STEPS,

    /** Awarded after walking a cumulative 1 km. */
    FIRST_KM,

    /** Awarded after walking a cumulative 10 km. */
    TEN_KM_WALKER,

    /** Awarded after walking a cumulative 50 km. */
    FIFTY_KM_WALKER,

    /** Awarded when trust score reaches 100. */
    TRUSTED_WALKER,

    /** Awarded when trust score reaches 500. */
    HIGHLY_TRUSTED
}
