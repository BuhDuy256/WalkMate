package com.walkmate.presentation.dto.response.gamification;

public record BadgeResponse(
        String badgeName,
        String displayName,
        String description,
        String awardedAt,
        String rarity,
        String category
) {}
