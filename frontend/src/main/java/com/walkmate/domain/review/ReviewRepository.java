package com.walkmate.domain.review;

import com.walkmate.domain.shared.DomainCallback;

import java.util.List;

public interface ReviewRepository {

    /**
     * Submits a review for a completed session.
     *
     * @param sessionId   the session being reviewed
     * @param ratingStars 1–5 star rating
     * @param comment     optional free-text comment (may be null)
     * @param callback    returns the created {@link WalkReview} on success
     */
    void submitReview(String sessionId, int ratingStars, String comment,
                      DomainCallback<WalkReview> callback);

    /**
     * Fetches all reviews written about a specific user.
     *
     * @param userId   the user whose reviews to load
     * @param callback returns the list of reviews (may be empty)
     */
    void getReviewsForUser(String userId, DomainCallback<List<WalkReview>> callback);
}
