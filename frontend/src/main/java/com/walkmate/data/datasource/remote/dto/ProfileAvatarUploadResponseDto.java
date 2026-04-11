package com.walkmate.data.datasource.remote.dto;

import com.google.gson.annotations.SerializedName;

public class ProfileAvatarUploadResponseDto {
    @SerializedName("avatar_url")
    private String avatarUrl;

    public String getAvatarUrl() {
        return avatarUrl;
    }
}
