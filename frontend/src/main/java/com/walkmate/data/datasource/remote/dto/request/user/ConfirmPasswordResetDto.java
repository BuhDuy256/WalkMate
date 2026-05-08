package com.walkmate.data.datasource.remote.dto.request.user;

public class ConfirmPasswordResetDto {
    private final String resetToken;
    private final String newPassword;

    public ConfirmPasswordResetDto(String resetToken, String newPassword) {
        this.resetToken  = resetToken;
        this.newPassword = newPassword;
    }

    public String getResetToken()  { return resetToken; }
    public String getNewPassword() { return newPassword; }
}
