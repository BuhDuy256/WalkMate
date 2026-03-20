package com.walkmate.domain.rating;

public interface RatingRepository {
    Rating submitRating(Rating rating) throws RatingException;
}
