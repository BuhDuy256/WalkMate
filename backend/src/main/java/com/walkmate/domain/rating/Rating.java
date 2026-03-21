package com.walkmate.domain.rating;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Rating Aggregate Root
 */
public class Rating {
    private UUID reviewId;
    private final UUID sessionId;
    private final UUID reviewerId;
    private final UUID revieweeId;
    private final RatingScore score;
    private final List<RatingTag> tags;
    private final RatingComment comment;
    private LocalDateTime createdAt;

    public Rating(
            UUID sessionId,
            UUID reviewerId,
            UUID revieweeId,
            RatingScore score,
            List<RatingTag> tags,
            RatingComment comment
    ) {
        this.sessionId = sessionId;
        this.reviewerId = reviewerId;
        this.revieweeId = revieweeId;
        this.score = score;
        this.tags = tags;
        this.comment = comment;
    }

    public Rating(
            UUID reviewId,
            UUID sessionId,
            UUID reviewerId,
            UUID revieweeId,
            RatingScore score,
            List<RatingTag> tags,
            RatingComment comment,
            LocalDateTime createdAt
    ) {
        this.reviewId = reviewId;
        this.sessionId = sessionId;
        this.reviewerId = reviewerId;
        this.revieweeId = revieweeId;
        this.score = score;
        this.tags = tags;
        this.comment = comment;
        this.createdAt = createdAt;
    }

    public UUID getReviewId() {
        return reviewId;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public UUID getReviewerId() {
        return reviewerId;
    }

    public UUID getRevieweeId() {
        return revieweeId;
    }

    public RatingScore getScore() {
        return score;
    }

    public List<RatingTag> getTags() {
        return tags;
    }

    public RatingComment getComment() {
        return comment;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setReviewId(UUID reviewId) {
        this.reviewId = reviewId;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "Rating{" +
                "reviewId=" + reviewId +
                ", sessionId=" + sessionId +
                ", reviewerId=" + reviewerId +
                ", revieweeId=" + revieweeId +
                ", score=" + score +
                ", tags=" + tags +
                ", comment=" + comment +
                ", createdAt=" + createdAt +
                '}';
    }
}
