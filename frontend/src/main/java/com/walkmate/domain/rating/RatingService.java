package com.walkmate.domain.rating;


public class RatingService {
    private final RatingRepository repository;

    public RatingService(RatingRepository repository) {
        this.repository = repository;
    }

    public Rating submitRating(Rating rating) throws RatingException {
        // Domain validation
        if (rating.getScore() == null) {
            throw new RatingException(
                    RatingErrorCode.RATING_INVALID_SCORE,
                    "Rating score is required"
            );
        }

        return repository.submitRating(rating);
    }
}
