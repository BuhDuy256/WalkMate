package com.walkmate.data.datasource.remote.dto.request.user;

public class VerifyPasswordResetDto {
    private final String email;
    private final String otp;

    public VerifyPasswordResetDto(String email, String otp) {
        this.email = email;
        this.otp   = otp;
    }

    public String getEmail() { return email; }
    public String getOtp()   { return otp; }
}
