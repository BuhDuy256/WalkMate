package com.walkmate.domain.rating;

import java.util.List;
import java.util.UUID;

/**
 * Rating Domain Model (Frontend)
 */
public class Rating {
    private final UUID sessionId;
    private final UUID reviewerId;
    private final UUID revieweeId;
    private final RatingScore score;
    private final List<RatingTag> tags;
    private final RatingComment comment;

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
}
