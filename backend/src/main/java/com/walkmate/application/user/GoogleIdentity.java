package com.walkmate.application.user;

/**
 * Verified identity claims extracted from a Firebase / Google ID token.
 *
 * @param sub        Google's stable unique user ID (the {@code sub} claim).
 * @param email      The user's Google email address.
 * @param name       The user's display name (may be null if not set on the Google account).
 * @param pictureUrl URL to the user's Google profile photo (may be null).
 */
public record GoogleIdentity(
        String sub,
        String email,
        String name,
        String pictureUrl
) {
}
