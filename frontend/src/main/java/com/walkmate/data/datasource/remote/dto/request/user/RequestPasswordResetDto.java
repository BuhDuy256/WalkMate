package com.walkmate.data.datasource.remote.dto.request.user;

public class RequestPasswordResetDto {
    private final String email;

    public RequestPasswordResetDto(String email) {
        this.email = email;
    }

    public String getEmail() { return email; }
}
