package com.walkmate.data.datasource.remote.dto.response.review;

import com.google.gson.annotations.SerializedName;

public class ReviewTagResponse {

    @SerializedName("tag_id")
    public String tagId;

    @SerializedName("tag_name")
    public String tagName;

    @SerializedName("tag_type")
    public String tagType;
}
