package com.walkmate.data.datasource.remote.dto.request.user;

import com.google.gson.annotations.SerializedName;

public class SetOrChangePasswordRequestDto {

    @SerializedName("currentPassword")
    private final String currentPassword;

    @SerializedName("newPassword")
    private final String newPassword;

    public SetOrChangePasswordRequestDto(String currentPassword, String newPassword) {
        this.currentPassword = currentPassword;
        this.newPassword     = newPassword;
    }
}
