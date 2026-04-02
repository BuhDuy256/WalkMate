package com.walkmate.domain.review;

import java.util.List;

public interface WalkReviewRepository {

    WalkReview save(WalkReview review);

    /** True when the reviewer has already submitted a review for this session. */
    boolean existsBySessionAndReviewer(String sessionId, String reviewerId);

    /** Returns all reviews written about a specific user (for their profile page). */
    List<WalkReview> findByRevieweeId(String revieweeId);
}
