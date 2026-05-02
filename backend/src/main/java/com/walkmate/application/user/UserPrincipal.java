package com.walkmate.application.user;

/**
 * Represents the authenticated caller extracted from the JWT.
 * Populated by UserPrincipalConverter and injected into controller methods
 * via @AuthenticationPrincipal.
 */
public record UserPrincipal(
        String userId,
        String email,
        String role
) {
}
