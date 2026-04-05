package com.walkmate.application.user;

/**
 * Command for the Google Sign-In use case.
 * Carries the raw Firebase ID token from the Android client.
 */
public record GoogleAuthCommand(String firebaseIdToken) {
}
