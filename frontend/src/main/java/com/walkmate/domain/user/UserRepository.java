package com.walkmate.domain.user;

import com.walkmate.domain.shared.DomainCallback;

public interface UserRepository {
    void login(String email, String password, DomainCallback<String> callback);
    // register only signals success (Void) — the caller redirects to login on success
    void register(String fullname, String email, String password, DomainCallback<Void> callback);
    void saveAccessToken(String token);
    String getAccessToken();
    // Registers or refreshes the FCM device token on the backend.
    // Silently no-ops if the user is not authenticated.
    void updateFcmToken(String token, DomainCallback<Void> callback);
}
