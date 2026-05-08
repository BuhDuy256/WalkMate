package com.walkmate.application.user;

public record VerifyPasswordResetCommand(String email, String otp) {}
