package com.walkmate.presentation.dto.response.gamification;

public record UserStatsResponse(
        String userId,
        int    totalPoints,
        double totalDistanceKm,
        int    completedSessions,
        int    trustScore
) {}
