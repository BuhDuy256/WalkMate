package com.walkmate.domain.gamification;

import java.util.List;
import java.util.Set;

public interface UserBadgeRepository {

    /** Returns the badge names (enum names) already awarded to a user. */
    Set<String> findBadgeNamesByUserId(String userId);

    /**
     * Persists new badge awards. Existing badges are silently skipped
     * (ON CONFLICT DO NOTHING).
     */
    void saveAll(String userId, List<Badge> badges);

    /** Returns all badge records for a user joined with badge metadata, ordered by awarded_at. */
    List<UserBadgeRecord> findByUserId(String userId);

    /** Returns all active badge definitions from the master catalog. */
    List<BadgeCatalogRecord> findAllActive();

    record UserBadgeRecord(
            String badgeName,
            String displayName,
            String description,
            String iconUrl,
            String awardedAt) {}

    record BadgeCatalogRecord(
            String name,
            String displayName,
            String description,
            String iconUrl) {}
}
