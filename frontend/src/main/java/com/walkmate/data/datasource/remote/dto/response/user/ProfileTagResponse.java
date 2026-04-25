package com.walkmate.data.datasource.remote.dto.response.user;

import com.google.gson.annotations.SerializedName;

public class ProfileTagResponse {

    @SerializedName("tagId")
    public String tagId;

    @SerializedName("tagName")
    public String tagName;
}
