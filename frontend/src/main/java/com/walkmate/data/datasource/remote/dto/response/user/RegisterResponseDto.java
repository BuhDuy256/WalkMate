package com.walkmate.data.datasource.remote.dto.response.user;

// Response payload for POST /api/v1/auth/register
// Only confirms which email was registered. Frontend redirects to login after success.
public class RegisterResponseDto {
    private String email;

    public String getEmail() {
        return email;
    }
}
