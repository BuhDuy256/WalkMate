package com.walkmate.presentation.dto.request.review;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record SubmitReviewRequest(

        @NotNull(message = "rating_stars is required")
        @Min(value = 1, message = "rating_stars must be at least 1")
        @Max(value = 5, message = "rating_stars must be at most 5")
        @JsonProperty("rating_stars")
        Integer ratingStars,

        @JsonProperty("comment")
        String comment,

        /** IDs of tags selected from {@code review_tag_master}; optional, may be null or empty. */
        @JsonProperty("tag_ids")
        List<UUID> tagIds
) {}
