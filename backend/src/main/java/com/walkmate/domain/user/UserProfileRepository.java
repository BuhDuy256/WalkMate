package com.walkmate.domain.user;

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
}
