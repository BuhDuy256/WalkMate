package com.walkmate.application.user;

/**
 * Contract for verifying a Firebase ID token obtained via Google Sign-In.
 *
 * <p>The application layer depends on this interface; the Firebase Admin SDK
 * implementation lives in infrastructure so the domain stays framework-free.</p>
 */
public interface GoogleTokenVerifier {

    /**
     * Verifies the given Firebase ID token and returns the extracted identity.
     *
     * @param firebaseIdToken the raw Firebase ID token string from the Android client
     * @return verified {@link GoogleIdentity} containing sub, email, name, pictureUrl
     * @throws com.walkmate.domain.shared.exception.DomainException with
     *         {@code USER_INVALID_CREDENTIALS} if the token is invalid, expired, or revoked
     */
    GoogleIdentity verify(String firebaseIdToken);
}
