package com.walkmate.domain.review;

import java.util.List;
import java.util.Optional;

public interface WalkReviewRepository {

    WalkReview save(WalkReview review);

    /** True when the reviewer has already submitted a review for this session. */
    boolean existsBySessionAndReviewer(String sessionId, String reviewerId);

    /** Returns all reviews written about a specific user (for their profile page). */
    List<WalkReview> findByRevieweeId(String revieweeId);

    /** Returns the review the caller submitted for a specific session, if any. */
    Optional<WalkReview> findBySessionAndReviewer(String sessionId, String reviewerId);
}
