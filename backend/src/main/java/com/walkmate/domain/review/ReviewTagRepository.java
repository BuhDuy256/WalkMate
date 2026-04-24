package com.walkmate.domain.review;

import java.util.List;
import java.util.UUID;

public interface ReviewTagRepository {

    /** Returns all active tags from {@code review_tag_master}, ordered by type then name. */
    List<ReviewTag> findAllActive();

    /**
     * Inserts rows into {@code walk_review_tag_map} for each tag selected by the reviewer.
     * Silently skips {@code tagIds} that do not exist in the master table (FK-safe insert).
     *
     * @param reviewId the newly created review's UUID string
     * @param tagIds   the selected tag UUIDs; may be empty
     */
    void saveTagMappings(String reviewId, List<UUID> tagIds);
}
