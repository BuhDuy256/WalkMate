package com.walkmate.domain.gamification;

/**
 * Aggregated statistics for one user, used by {@link BadgePolicy} and
 * the gamification leaderboard.
 *
 * <p>Fields mirror the denormalized columns on {@code user_account} that are
 * incremented at session completion by {@code GamificationCommandService}.</p>
 */
public record UserStats(
        String userId,
        int    completedSessions,
        double totalDistanceKm,
        int    totalPoints,
        int    trustScore
) {}
