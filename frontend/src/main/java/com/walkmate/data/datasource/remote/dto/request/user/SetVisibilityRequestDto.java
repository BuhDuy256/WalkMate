package com.walkmate.data.datasource.remote.dto.request.user;

public class SetVisibilityRequestDto {
    private final String mode;

    public SetVisibilityRequestDto(String mode) {
        this.mode = mode;
    }

    public String getMode() {
        return mode;
    }
}
