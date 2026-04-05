package com.walkmate.data.datasource.remote.dto.request.user;

public class GoogleLoginRequestDto {
    private final String idToken;

    public GoogleLoginRequestDto(String idToken) {
        this.idToken = idToken;
    }

    public String getIdToken() {
        return idToken;
    }
}
