package com.walkmate.data.datasource.remote.dto.response.user;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class UserProfileResponse {

    @SerializedName("userId")
    public String userId;

    @SerializedName("fullName")
    public String fullName;

    @SerializedName("gender")
    public String gender;

    @SerializedName("dateOfBirth")
    public String dateOfBirth;

    @SerializedName("avatarUrl")
    public String avatarUrl;

    @SerializedName("bio")
    public String bio;

    @SerializedName("trustScore")
    public int trustScore;

    @SerializedName("totalDistanceKm")
    public double totalDistanceKm;

    @SerializedName("totalSessions")
    public int totalSessions;

    @SerializedName("tags")
    public List<String> tags;
}
