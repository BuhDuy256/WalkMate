package com.walkmate.data.datasource.remote.dto.response.gamification;

import com.google.gson.annotations.SerializedName;

public class BadgeResponse {

    @SerializedName("badgeName")
    public String badgeName;

    @SerializedName("awardedAt")
    public String awardedAt;
}
