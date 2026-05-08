package com.walkmate.application.user;

public record ConfirmPasswordResetCommand(String resetToken, String newPassword) {}
