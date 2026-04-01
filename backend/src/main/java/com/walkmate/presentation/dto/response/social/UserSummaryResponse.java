package com.walkmate.presentation.dto.response.social;

/**
 * Lightweight user representation returned in follower / following lists.
 * Consumers who need the full profile should call GET /api/v1/users/{userId}.
 */
public record UserSummaryResponse(String userId, String fullName, String avatarUrl) {}
