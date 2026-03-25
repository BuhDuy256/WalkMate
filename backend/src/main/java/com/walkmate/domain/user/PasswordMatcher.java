package com.walkmate.domain.user;

public interface PasswordMatcher {
    boolean matches(String rawPassword, String encodedPassword);
}
