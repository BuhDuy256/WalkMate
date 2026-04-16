package com.walkmate.domain.user;

/** Strategy for verifying a raw OTP code against its stored hash. */
@FunctionalInterface
public interface OtpCodeMatcher {
    boolean matches(String rawCode, String storedHash);
}
