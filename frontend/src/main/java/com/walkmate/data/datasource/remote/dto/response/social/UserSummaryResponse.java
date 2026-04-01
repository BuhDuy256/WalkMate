package com.walkmate.data.datasource.remote.dto.response.social;

import com.google.gson.annotations.SerializedName;

public class UserSummaryResponse {

    @SerializedName("userId")
    public String userId;

    @SerializedName("fullName")
    public String fullName;

    @SerializedName("avatarUrl")
    public String avatarUrl;
}
