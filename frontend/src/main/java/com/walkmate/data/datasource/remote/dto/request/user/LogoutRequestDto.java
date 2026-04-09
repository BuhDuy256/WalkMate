package com.walkmate.data.datasource.remote.dto.request.user;

public class LogoutRequestDto {
    private final String deviceId;

    public LogoutRequestDto(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getDeviceId() {
        return deviceId;
    }
}
