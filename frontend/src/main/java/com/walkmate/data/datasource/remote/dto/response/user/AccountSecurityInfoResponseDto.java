package com.walkmate.data.datasource.remote.dto.response.user;

import com.google.gson.annotations.SerializedName;

public class AccountSecurityInfoResponseDto {

    @SerializedName("hasPassword")
    private boolean hasPassword;

    @SerializedName("hasGoogle")
    private boolean hasGoogle;

    public boolean isHasPassword() { return hasPassword; }
    public boolean isHasGoogle()   { return hasGoogle; }
}
