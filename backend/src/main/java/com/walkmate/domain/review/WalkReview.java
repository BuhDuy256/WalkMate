package com.walkmate.domain.review;

import com.walkmate.domain.shared.exception.DomainException;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
public class WalkReview {

    private String  reviewId;
    private String  sessionId;
    private String  reviewerId;
    private String  revieweeId;
    private int     ratingStars;
    private String  comment;
    private Instant createdAt;

    protected WalkReview() {}

    /** Rehydration constructor — used by the repository when loading from DB. */
    public WalkReview(String reviewId, String sessionId, String reviewerId, String revieweeId,
                      int ratingStars, String comment, Instant createdAt) {
        this.reviewId    = reviewId;
        this.sessionId   = sessionId;
        this.reviewerId  = reviewerId;
        this.revieweeId  = revieweeId;
        this.ratingStars = ratingStars;
        this.comment     = comment;
        this.createdAt   = createdAt;
    }

    private WalkReview(String sessionId, String reviewerId, String revieweeId,
                       int ratingStars, String comment) {
        if (ratingStars < 1 || ratingStars > 5) {
            throw new DomainException(ReviewErrorCode.REVIEW_INVALID_RATING);
        }
        this.reviewId    = UUID.randomUUID().toString();
        this.sessionId   = sessionId;
        this.reviewerId  = reviewerId;
        this.revieweeId  = revieweeId;
        this.ratingStars = ratingStars;
        this.comment     = comment;
        this.createdAt   = Instant.now();
    }

    public static WalkReview create(String sessionId, String reviewerId, String revieweeId,
                                    int ratingStars, String comment) {
        return new WalkReview(sessionId, reviewerId, revieweeId, ratingStars, comment);
    }
}
