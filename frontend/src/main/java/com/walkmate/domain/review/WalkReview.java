package com.walkmate.domain.review;

public class WalkReview {

    private final String sessionId;
    private final String reviewId;
    private final String reviewerId;
    private final String revieweeId;
    private final int    ratingStars;
    private final String comment;
    private final String createdAt;   // ISO-8601 string

    public WalkReview(String reviewId, String sessionId,
                      String reviewerId, String revieweeId,
                      int ratingStars, String comment, String createdAt) {
        this.reviewId    = reviewId;
        this.sessionId   = sessionId;
        this.reviewerId  = reviewerId;
        this.revieweeId  = revieweeId;
        this.ratingStars = ratingStars;
        this.comment     = comment;
        this.createdAt   = createdAt;
    }

    public String getReviewId()    { return reviewId; }
    public String getSessionId()   { return sessionId; }
    public String getReviewerId()  { return reviewerId; }
    public String getRevieweeId()  { return revieweeId; }
    public int    getRatingStars() { return ratingStars; }
    public String getComment()     { return comment; }
    public String getCreatedAt()   { return createdAt; }
}
