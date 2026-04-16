package com.walkmate.data.datasource.remote.dto.request.user;

public class VerifyOtpRequestDto {
    private final String phone;
    private final String code;
    private final String deviceId;

    public VerifyOtpRequestDto(String phone, String code, String deviceId) {
        this.phone = phone;
        this.code = code;
        this.deviceId = deviceId;
    }

    public String getPhone() {
        return phone;
    }

    public String getCode() {
        return code;
    }

    public String getDeviceId() {
        return deviceId;
    }
}
