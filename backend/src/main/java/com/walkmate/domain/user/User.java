package com.walkmate.domain.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Entity
@Table(name = "user_account")
public class User {

    private static final AuthProvider DEFAULT_PROVIDER = AuthProvider.LOCAL;
    private static final AccountStatus DEFAULT_STATUS = AccountStatus.ACTIVE;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, unique = true, updatable = false)
    private String email;

    @Column(unique = true, length = 20)
    private String phone;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "auth_provider")
    private AuthProvider provider;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "account_status")
    private AccountStatus status;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    protected User() {
    }

    private User(String fullName, String email, String passwordHash) {
        requireText(fullName, "Full name is required");
        this.email = normalizeEmail(email);
        this.phone = null;
        this.provider = DEFAULT_PROVIDER;
        this.status = DEFAULT_STATUS;
        this.passwordHash = requireText(passwordHash, "Password hash is required");
        this.lastLoginAt = null;
    }

    public static User register(String fullName, String email, String passwordHash) {
        return new User(fullName, email, passwordHash);
    }

    public static String normalizeEmail(String email) {
        return requireText(email, "Email is required").trim().toLowerCase(Locale.ROOT);
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}