package com.walkmate.data.datasource.remote.dto.request.user;

public class UpdateFcmTokenRequestDto {

    private final String fcmToken;

    public UpdateFcmTokenRequestDto(String fcmToken) {
        this.fcmToken = fcmToken;
    }

    public String getFcmToken() {
        return fcmToken;
    }
}
