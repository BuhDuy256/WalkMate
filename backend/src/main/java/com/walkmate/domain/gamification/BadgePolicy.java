package com.walkmate.domain.gamification;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Domain policy that determines which badges a user has newly earned.
 *
 * <p>Each badge is awarded at most once (enforced both here and by the
 * {@code UNIQUE(user_id, badge_name)} DB constraint). The caller must pass
 * the set of badge names already held by the user.</p>
 */
public final class BadgePolicy {

    private BadgePolicy() {}

    /**
     * Evaluates which badges the user has newly earned given their current stats.
     *
     * @param stats          the user's current aggregated statistics
     * @param existingBadges badge names the user already holds (case-sensitive enum names)
     * @return list of newly earned badges (may be empty; never null)
     */
    public static List<Badge> evaluateEarned(UserStats stats, Set<String> existingBadges) {
        List<Badge> newBadges = new ArrayList<>();

        checkAndAdd(Badge.FIRST_WALK,    stats.completedSessions() >= 1,           existingBadges, newBadges);
        checkAndAdd(Badge.FIRST_FIVE,    stats.completedSessions() >= 5,           existingBadges, newBadges);
        checkAndAdd(Badge.CENTURY_STEPS, stats.completedSessions() >= 10,          existingBadges, newBadges);
        checkAndAdd(Badge.FIRST_KM,      stats.totalDistanceKm() >= 1.0,           existingBadges, newBadges);
        checkAndAdd(Badge.TEN_KM_WALKER, stats.totalDistanceKm() >= 10.0,          existingBadges, newBadges);
        checkAndAdd(Badge.FIFTY_KM_WALKER, stats.totalDistanceKm() >= 50.0,        existingBadges, newBadges);
        checkAndAdd(Badge.TRUSTED_WALKER,  stats.trustScore() >= 100,              existingBadges, newBadges);
        checkAndAdd(Badge.HIGHLY_TRUSTED,  stats.trustScore() >= 500,              existingBadges, newBadges);

        return newBadges;
    }

    private static void checkAndAdd(Badge badge, boolean criteriaMet,
                                    Set<String> existing, List<Badge> target) {
        if (criteriaMet && !existing.contains(badge.name())) {
            target.add(badge);
        }
    }
}
