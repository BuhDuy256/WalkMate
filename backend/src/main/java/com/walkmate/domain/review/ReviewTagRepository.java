package com.walkmate.domain.review;

import java.util.List;
import java.util.UUID;

public interface ReviewTagRepository {

    /** Returns all active tags from {@code review_tag_master}, ordered by type then name. */
    List<ReviewTag> findAllActive();

    /**
     * Returns the subset of tags whose IDs are in the supplied list.
     * Missing IDs are silently absent from the result.
     */
    List<ReviewTag> findByIds(List<UUID> tagIds);

    /**
     * Inserts rows into {@code walk_review_tag_map} for each tag selected by the reviewer.
     * Silently skips {@code tagIds} that do not exist in the master table (FK-safe insert).
     *
     * @param reviewId the newly created review's UUID string
     * @param tagIds   the selected tag UUIDs; may be empty
     */
    void saveTagMappings(String reviewId, List<UUID> tagIds);

    /** Returns the tag UUIDs (as strings) stored in {@code walk_review_tag_map} for a review. */
    List<String> findTagIdsByReviewId(String reviewId);
}
