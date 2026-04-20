package com.walkmate.data.mapper;

import com.walkmate.data.datasource.local.entity.UserProfileEntity;
import com.walkmate.domain.user.UserProfile;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Converts between the Room {@link UserProfileEntity} (local cache) and the
 * {@link UserProfile} domain object.
 *
 * This mapper sits alongside {@link UserProfileMapper} (DTO → Domain) and follows
 * the same static-method-only pattern. It is the only place that knows about
 * the comma-separated tag encoding used in the database.
 */
public final class UserProfileEntityMapper {

    private UserProfileEntityMapper() {}

    /** Domain → Entity (called before dao.upsert). */
    public static UserProfileEntity toEntity(UserProfile domain, long cachedAtEpochMs) {
        UserProfileEntity e = new UserProfileEntity();
        e.userId          = domain.getUserId();
        e.fullName        = domain.getFullName();
        e.gender          = domain.getGender();
        e.dateOfBirth     = domain.getDateOfBirth();
        e.avatarUrl       = domain.getAvatarUrl();
        e.bio             = domain.getBio();
        e.searchRadius    = domain.getSearchRadius();
        e.trustScore      = domain.getTrustScore();
        e.totalDistanceKm = domain.getTotalDistanceKm();
        e.totalSessions   = domain.getTotalSessions();
        e.tags            = joinTags(domain.getTags());
        e.cachedAtEpochMs = cachedAtEpochMs;
        return e;
    }

    /** Entity → Domain (called after dao.getProfileById returns a cached row). */
    public static UserProfile toDomain(UserProfileEntity e) {
        return new UserProfile(
                e.userId,
                e.fullName,
                e.gender,
                e.dateOfBirth,
                e.avatarUrl,
                e.bio,
                e.searchRadius,
                e.trustScore,
                e.totalDistanceKm,
                e.totalSessions,
                splitTags(e.tags)
        );
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private static String joinTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tags.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(tags.get(i));
        }
        return sb.toString();
    }

    private static List<String> splitTags(String tags) {
        if (tags == null || tags.trim().isEmpty()) return Collections.emptyList();
        return Collections.unmodifiableList(Arrays.asList(tags.split(",")));
    }
}
