package com.walkmate.domain.rating;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for Rating
 * Interface lives in domain, implementation in infrastructure
 */
public interface RatingRepository {

    /**
     * Save a new rating
     */
    Rating save(Rating rating);

    /**
     * Check if user has already rated this session
     */
    boolean existsBySessionAndReviewer(UUID sessionId, UUID reviewerId);

    /**
     * Find rating by review ID
     */
    Optional<Rating> findById(UUID reviewId);

    /**
     * Find rating by session and reviewer
     */
    Optional<Rating> findBySessionAndReviewer(UUID sessionId, UUID reviewerId);
}
