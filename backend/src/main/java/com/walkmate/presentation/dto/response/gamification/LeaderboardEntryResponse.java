package com.walkmate.presentation.dto.response.gamification;

public record LeaderboardEntryResponse(
        int    rank,
        String userId,
        int    totalPoints,
        double totalDistanceKm,
        int    completedSessions,
        int    trustScore
) {}
