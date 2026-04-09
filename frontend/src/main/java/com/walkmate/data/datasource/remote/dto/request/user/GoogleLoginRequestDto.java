package com.walkmate.data.datasource.remote.dto.request.user;

public class GoogleLoginRequestDto {
    private final String idToken;
    private final String deviceId;

    public GoogleLoginRequestDto(String idToken, String deviceId) {
        this.idToken = idToken;
        this.deviceId = deviceId;
    }

    public String getIdToken() {
        return idToken;
    }

    public String getDeviceId() {
        return deviceId;
    }
}
