package com.walkmate.data.datasource.remote.dto.response.review;

import com.google.gson.annotations.SerializedName;

public class ReviewResponse {

    @SerializedName("review_id")
    private String reviewId;

    @SerializedName("session_id")
    private String sessionId;

    @SerializedName("reviewer_id")
    private String reviewerId;

    @SerializedName("reviewee_id")
    private String revieweeId;

    @SerializedName("rating_stars")
    private int ratingStars;

    @SerializedName("comment")
    private String comment;

    @SerializedName("created_at")
    private String createdAt;   // ISO-8601

    public String getReviewId()    { return reviewId; }
    public String getSessionId()   { return sessionId; }
    public String getReviewerId()  { return reviewerId; }
    public String getRevieweeId()  { return revieweeId; }
    public int    getRatingStars() { return ratingStars; }
    public String getComment()     { return comment; }
    public String getCreatedAt()   { return createdAt; }
}
