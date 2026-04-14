package com.walkmate.data.datasource.remote.dto.response.social;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class PublicUserResponse {

    @SerializedName("userId")
    public String userId;

    @SerializedName("fullName")
    public String fullName;

    @SerializedName("avatarUrl")
    public String avatarUrl;

    @SerializedName("bio")
    public String bio;

    @SerializedName("tags")
    public List<String> tags;

    @SerializedName("trustScore")
    public float trustScore;

    @SerializedName("gender")
    public String gender;

    @SerializedName("friendshipStatus")
    public String friendshipStatus;
}
