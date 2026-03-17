package com.walkmate.presentation.util;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

public final class UserIdentityExtractor {
    private UserIdentityExtractor() {
    }

    public static UUID extractUserId(Jwt jwt, String userIdHeader) {
        try {
            if (jwt != null && jwt.getSubject() != null && !jwt.getSubject().isBlank()) {
                return UUID.fromString(jwt.getSubject());
            }
            if (userIdHeader != null && !userIdHeader.isBlank()) {
                return UUID.fromString(userIdHeader);
            }
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid user id format");
        }

        throw new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "Missing user identity. Provide JWT or X-User-Id header");
    }
}