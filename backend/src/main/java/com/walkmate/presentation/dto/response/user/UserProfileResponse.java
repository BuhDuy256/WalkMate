package com.walkmate.presentation.dto.response.user;

import java.util.List;

public record UserProfileResponse(
        String       userId,
        String       fullName,
        String       gender,
        String       dateOfBirth,      // "YYYY-MM-DD" or null
        String       avatarUrl,
        String       bio,
        int          trustScore,
        double       totalDistanceKm,
        int          totalSessions,
        List<String> tags,
        String       lastActiveAt,     // null unless caller is an accepted friend
        String       friendshipStatus, // "NONE" | "PENDING_SENT" | "PENDING_RECEIVED" | "FRIENDS"; null for own profile
        String       pendingRequestId  // non-null only when friendshipStatus == "PENDING_RECEIVED"
) {}
