package com.walkmate.data.datasource.remote.dto.request.review;

import com.google.gson.annotations.SerializedName;

public class SubmitReviewRequest {

    @SerializedName("rating_stars")
    private final int ratingStars;

    @SerializedName("comment")
    private final String comment;  // nullable

    public SubmitReviewRequest(int ratingStars, String comment) {
        this.ratingStars = ratingStars;
        this.comment     = comment;
    }

    public int    getRatingStars() { return ratingStars; }
    public String getComment()     { return comment; }
}
