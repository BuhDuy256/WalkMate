package com.walkmate.data.datasource.remote.dto.request.user;

public class SendOtpRequestDto {
    private final String phone;

    public SendOtpRequestDto(String phone) {
        this.phone = phone;
    }

    public String getPhone() {
        return phone;
    }
}
