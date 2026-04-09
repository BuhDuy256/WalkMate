package com.walkmate.data.datasource.remote.dto.request.user;

public class RegisterRequestDto {
    private final String fullname;
    private final String email;
    private final String password;
    private final String deviceId;

    public RegisterRequestDto(String fullname, String email, String password, String deviceId) {
        this.fullname = fullname;
        this.email = email;
        this.password = password;
        this.deviceId = deviceId;
    }

    public String getFullname() {
        return fullname;
    }

    public String getEmail() {
        return email;
    }

    public String getPassword() {
        return password;
    }

    public String getDeviceId() {
        return deviceId;
    }
}
