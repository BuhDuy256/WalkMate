package com.walkmate.data.datasource.remote.dto.response.gamification;

import com.google.gson.annotations.SerializedName;

public class UserStatsResponse {

    @SerializedName("userId")
    public String userId;

    @SerializedName("totalPoints")
    public int totalPoints;

    @SerializedName("totalDistanceKm")
    public double totalDistanceKm;

    @SerializedName("completedSessions")
    public int completedSessions;

    @SerializedName("trustScore")
    public int trustScore;
}
