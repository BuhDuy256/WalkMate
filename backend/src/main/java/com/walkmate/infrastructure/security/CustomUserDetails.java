package com.walkmate.infrastructure.security;

import java.util.UUID;

// Stub for Spring Security CustomUserDetails
public class CustomUserDetails {
    private final UUID id;
    private final String email;
    private final String role;

    public CustomUserDetails(UUID id, String email, String role) {
        this.id = id;
        this.email = email;
        this.role = role;
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getRole() {
        return role;
    }
}
