package com.walkmate.presentation.dto.response.review;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ReviewResponse(

        @JsonProperty("review_id") String reviewId,

        @JsonProperty("session_id") String sessionId,

        @JsonProperty("reviewer_id") String reviewerId,

        @JsonProperty("reviewee_id") String revieweeId,

        @JsonProperty("rating_stars") int ratingStars,

        @JsonProperty("comment") String comment,

        @JsonProperty("created_at") String createdAt // ISO-8601
) {
}
