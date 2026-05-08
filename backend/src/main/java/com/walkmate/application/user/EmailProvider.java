package com.walkmate.application.user;

public interface EmailProvider {
    void sendOtp(String toEmail, String otpCode);
}
