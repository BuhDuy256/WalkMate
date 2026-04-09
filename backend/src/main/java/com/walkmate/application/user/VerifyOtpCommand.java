package com.walkmate.application.user;

public record VerifyOtpCommand(String phone, String code, String deviceId) {
}
