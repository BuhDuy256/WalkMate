package com.walkmate.domain.user;

import com.walkmate.domain.shared.DomainCallback;

public interface UserRepository {

    // ── Authentication ────────────────────────────────────────────────────────

    void login(String email, String password, String deviceId, DomainCallback<String> callback);

    /** Auto-login: register returns the access token directly on success. */
    void register(String fullname, String email, String password, String deviceId, DomainCallback<String> callback);

    void loginWithGoogle(String googleIdToken, String deviceId, DomainCallback<String> callback);

    void verifyOtp(String phone, String code, DomainCallback<String> callback);

    // ── Session management ────────────────────────────────────────────────────

    /** Invalidates the current device's refresh token on the backend, then clears the local session. */
    void logout(DomainCallback<Void> callback);

    /** Invalidates all refresh tokens for this user across all devices, then clears the local session. */
    void logoutAll(DomainCallback<Void> callback);

    // ── OTP ───────────────────────────────────────────────────────────────────

    void sendOtp(String phone, DomainCallback<Void> callback);

    // ── User settings ─────────────────────────────────────────────────────────

    void setVisibility(VisibilityMode mode, DomainCallback<Void> callback);

    // ── Token / device helpers ────────────────────────────────────────────────

    void saveAccessToken(String token);

    String getAccessToken();

    /** Returns the persistent device ID, generating one if it does not yet exist. */
    String getOrGenerateDeviceId();

    // ── FCM ───────────────────────────────────────────────────────────────────

    /** Registers or refreshes the FCM device token on the backend. No-ops if not authenticated. */
    void updateFcmToken(String token, DomainCallback<Void> callback);
}
