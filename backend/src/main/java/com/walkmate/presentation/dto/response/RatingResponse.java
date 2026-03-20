package com.walkmate.presentation.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for Rating
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class RatingResponse {
    private UUID reviewId;
    private UUID sessionId;
    private UUID reviewerId;
    private UUID revieweeId;
    private Integer score;
    private List<String> tags;
    private String comment;
    private LocalDateTime createdAt;
}
