package com.walkmate.domain.user;

import com.walkmate.domain.shared.DomainCallback;

public interface UserRepository {
    void login(String email, String password, DomainCallback<String> callback);
    void register(String fullname, String email, String password, DomainCallback<User> callback);
    void saveAccessToken(String token);
    String getAccessToken();
}
