package com.walkmate.presentation.dto.request.rating;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/**
 * Request DTO for submitting a rating
 */
@Data
public class SubmitRatingRequest {

    @NotNull(message = "User ID is required")
    private UUID userId;

    @NotNull(message = "Session ID is required")
    private UUID sessionId;

    @NotNull(message = "Reviewee ID is required")
    private UUID revieweeId;

    @NotNull(message = "Rating score is required")
    @Min(value = 1, message = "Rating must be at least 1")
    @Max(value = 5, message = "Rating must be at most 5")
    private Integer score;

    private List<String> tags;

    @Size(max = 500, message = "Comment cannot exceed 500 characters")
    private String note;
}
