package com.walkmate.data.datasource.remote.dto.response.gamification;

import com.google.gson.annotations.SerializedName;

public class BadgeResponse {

    @SerializedName("badgeName")
    public String badgeName;

    @SerializedName("displayName")
    public String displayName;

    @SerializedName("description")
    public String description;

    @SerializedName("iconUrl")
    public String iconUrl;

    @SerializedName("awardedAt")
    public String awardedAt;
}
