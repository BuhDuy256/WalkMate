package com.walkmate.domain.user;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserProfileRepository {

    Optional<UserProfile> findByUserId(UUID userId);

    UserProfile save(UserProfile profile);

    /** Replaces all tags for the given user atomically. */
    void replaceTags(UUID userId, List<String> tags);

    /** Returns all tag names for a user, ordered by insertion order. */
    List<String> findTagsByUserId(UUID userId);
}
