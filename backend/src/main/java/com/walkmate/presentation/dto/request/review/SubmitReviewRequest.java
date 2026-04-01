package com.walkmate.presentation.dto.request.review;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record SubmitReviewRequest(

        @NotNull(message = "rating_stars is required")
        @Min(value = 1, message = "rating_stars must be at least 1")
        @Max(value = 5, message = "rating_stars must be at most 5")
        @JsonProperty("rating_stars")
        Integer ratingStars,

        @JsonProperty("comment")
        String comment
) {}
