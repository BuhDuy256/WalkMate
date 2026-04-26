package com.walkmate.domain.user;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface UserProfileRepository {

    Optional<UserProfile> findByUserId(UUID userId);

    UserProfile save(UserProfile profile);

    /** Replaces all tag mappings for the given user using master tag IDs (wipe-and-replace). */
    void replaceTagsByIds(UUID userId, List<UUID> tagIds);

    /** Returns all tag names for a user, ordered by tag name. */
    List<String> findTagsByUserId(UUID userId);

    /** Returns all master tags available for selection. */
    List<ProfileTagMaster> findAllMasterTags();

    /** Batch-fetches full names for the given user IDs. Missing IDs are absent from the map. */
    Map<UUID, String> findNamesByUserIds(Collection<UUID> userIds);

    /**
     * Batch-fetches (fullName + avatarUrl) snapshots for the given user IDs.
     * Used by session-history enrichment to avoid N+1 profile lookups.
     * Missing IDs are absent from the map.
     */
    Map<UUID, UserProfileSnapshot> findSnapshotsByUserIds(Collection<UUID> userIds);

    /** Returns the last_active_at timestamp for a single user, or null if the row is absent. */
    Instant findLastActiveAtById(UUID userId);
}
