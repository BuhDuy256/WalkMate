package com.walkmate.presentation.dto.response.user;

import java.util.List;

public record UserProfileResponse(
        String       userId,
        String       fullName,
        String       gender,
        String       dateOfBirth,   // "YYYY-MM-DD" or null
        String       avatarUrl,
        String       bio,
        int          trustScore,
        double       totalDistanceKm,
        int          totalSessions,
        List<String> tags
) {}
