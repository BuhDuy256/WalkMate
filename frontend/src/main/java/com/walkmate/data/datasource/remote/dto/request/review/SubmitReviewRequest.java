package com.walkmate.data.datasource.remote.dto.request.review;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class SubmitReviewRequest {

    @SerializedName("rating_stars")
    private final int ratingStars;

    @SerializedName("comment")
    private final String comment;  // nullable

    @SerializedName("tag_ids")
    private final List<String> tagIds;  // nullable/empty → no tag mappings persisted

    public SubmitReviewRequest(int ratingStars, String comment, List<String> tagIds) {
        this.ratingStars = ratingStars;
        this.comment     = comment;
        this.tagIds      = tagIds;
    }

    public int          getRatingStars() { return ratingStars; }
    public String       getComment()     { return comment; }
    public List<String> getTagIds()      { return tagIds; }
}
