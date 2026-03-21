package com.walkmate.domain.rating;

import java.util.Optional;
import java.util.UUID;


public interface RatingRepository {

  
    Rating save(Rating rating);

  
    boolean existsBySessionAndReviewer(UUID sessionId, UUID reviewerId);

    Optional<Rating> findById(UUID reviewId);


    Optional<Rating> findBySessionAndReviewer(UUID sessionId, UUID reviewerId);
}
