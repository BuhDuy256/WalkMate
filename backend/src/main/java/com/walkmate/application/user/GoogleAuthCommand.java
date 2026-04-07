package com.walkmate.application.user;

/**
 * Command for the Google Sign-In use case.
 * Carries the raw Firebase ID token and the calling device identifier.
 */
public record GoogleAuthCommand(String firebaseIdToken, String deviceId) {
}
