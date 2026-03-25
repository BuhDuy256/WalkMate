package com.walkmate.domain.user;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import lombok.Getter;
import com.walkmate.domain.shared.exception.DomainException;

@Getter
public class User {

    private static final AuthProvider DEFAULT_PROVIDER = AuthProvider.LOCAL;
    private static final AccountStatus DEFAULT_STATUS = AccountStatus.ACTIVE;

    private UUID userId;
    private String email;
    private String phone;
    private AuthProvider provider;
    private AccountStatus status;
    private String passwordHash;
    private Instant createdAt;
    private Instant lastLoginAt;

    protected User() {
    }

    // Rehydration constructor
    public User(UUID userId, String email, String phone, AuthProvider provider, AccountStatus status,
            String passwordHash, Instant createdAt, Instant lastLoginAt) {
        this.userId = userId;
        this.email = email;
        this.phone = phone;
        this.provider = provider;
        this.status = status;
        this.passwordHash = passwordHash;
        this.createdAt = createdAt;
        this.lastLoginAt = lastLoginAt;
    }

    private User(String fullName, String email, String passwordHash) {
        requireText(fullName, "Full name is required");
        this.email = normalizeEmail(email);
        this.phone = null;
        this.provider = DEFAULT_PROVIDER;
        this.status = DEFAULT_STATUS;
        this.passwordHash = requireText(passwordHash, "Password hash is required");
        this.lastLoginAt = null;
        this.createdAt = Instant.now();
    }

    public static User register(String fullName, String email, String passwordHash) {
        return new User(fullName, email, passwordHash);
    }

    public static String normalizeEmail(String email) {
        return requireText(email, "Email is required").trim().toLowerCase(Locale.ROOT);
    }

    public void authenticate(String rawPassword, PasswordMatcher matcher) {
        if (!matcher.matches(rawPassword, this.passwordHash)) {
            throw new DomainException(UserErrorCode.USER_INVALID_CREDENTIALS);
        }
        this.lastLoginAt = Instant.now();
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new DomainException(UserErrorCode.INVALID_USER_DATA, message);
        }
        return value.trim();
    }
}
