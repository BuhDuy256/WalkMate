package com.walkmate.data.datasource.remote.dto;

public class ProfileSetupAckResponseDto {
    private boolean success;
    private String message;

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }
}
