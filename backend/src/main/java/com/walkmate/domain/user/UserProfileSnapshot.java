package com.walkmate.domain.user;

/**
 * Lightweight read-only projection used by batch queries that need
 * only a user's display name and avatar URL (e.g. session history enrichment).
 */
public record UserProfileSnapshot(String fullName, String avatarUrl) {}
