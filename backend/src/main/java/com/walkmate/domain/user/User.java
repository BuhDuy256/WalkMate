package com.walkmate.domain.user;

import com.walkmate.domain.shared.exception.DomainException;
import lombok.Getter;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Getter
public class User {

    private static final AuthProvider  DEFAULT_PROVIDER = AuthProvider.LOCAL;
    private static final AccountStatus DEFAULT_STATUS   = AccountStatus.ACTIVE;

    private UUID          userId;
    private String        email;
    private String        phone;
    private AuthProvider  provider;
    private AccountStatus status;
    private String        passwordHash;
    private Instant       createdAt;
    private Instant       lastLoginAt;
    private int           trustScore;
    private int           totalPoints;
    private double        totalDistanceKm;
    private int           completedSessions;
    private String        fcmToken;

    protected User() {}

    // ── Rehydration constructor (repository → domain) ─────────────────────────

    public User(UUID userId, String email, String phone, AuthProvider provider, AccountStatus status,
                String passwordHash, Instant createdAt, Instant lastLoginAt,
                int trustScore, int totalPoints, double totalDistanceKm, int completedSessions,
                String fcmToken) {
        this.userId            = userId;
        this.email             = email;
        this.phone             = phone;
        this.provider          = provider;
        this.status            = status;
        this.passwordHash      = passwordHash;
        this.createdAt         = createdAt;
        this.lastLoginAt       = lastLoginAt;
        this.trustScore        = trustScore;
        this.totalPoints       = totalPoints;
        this.totalDistanceKm   = totalDistanceKm;
        this.completedSessions = completedSessions;
        this.fcmToken          = fcmToken;
    }

    // ── Creation factory ──────────────────────────────────────────────────────

    private User(String fullName, String email, String passwordHash) {
        requireText(fullName, "Full name is required");
        this.email        = normalizeEmail(email);
        this.phone        = null;
        this.provider     = DEFAULT_PROVIDER;
        this.status       = DEFAULT_STATUS;
        this.passwordHash = requireText(passwordHash, "Password hash is required");
        this.lastLoginAt  = null;
        this.createdAt         = Instant.now();
        this.trustScore        = 0;
        this.totalPoints       = 0;
        this.totalDistanceKm   = 0.0;
        this.completedSessions = 0;
        this.fcmToken          = null;
    }

    public static User register(String fullName, String email, String passwordHash) {
        return new User(fullName, email, passwordHash);
    }

    // ── Domain behaviour ──────────────────────────────────────────────────────

    public static String normalizeEmail(String email) {
        return requireText(email, "Email is required").trim().toLowerCase(Locale.ROOT);
    }

    public void authenticate(String rawPassword, PasswordMatcher matcher) {
        if (!matcher.matches(rawPassword, this.passwordHash)) {
            throw new DomainException(UserErrorCode.USER_INVALID_CREDENTIALS);
        }
        this.lastLoginAt = Instant.now();
    }

    /**
     * Sets the trust score to an already-bounded value produced by
     * {@link com.walkmate.domain.review.TrustScorePolicy}.
     */
    public void applyTrustScore(int boundedScore) {
        this.trustScore = boundedScore;
    }

    /** Adds the gamification results from one completed session. */
    public void applySessionReward(int points, double distanceKm) {
        this.totalPoints       += points;
        this.totalDistanceKm   += distanceKm;
        this.completedSessions += 1;
    }

    /** Stores the FCM device token used to push notifications to this user's device. */
    public void updateFcmToken(String token) {
        this.fcmToken = token;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new DomainException(UserErrorCode.INVALID_USER_DATA, message);
        }
        return value.trim();
    }
}
