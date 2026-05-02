package com.walkmate.domain.user;

import com.walkmate.domain.shared.exception.DomainException;
import lombok.Getter;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@Getter
public class User {

    private static final AuthProvider  DEFAULT_PROVIDER = AuthProvider.LOCAL;
    private static final AccountStatus DEFAULT_STATUS   = AccountStatus.ACTIVE;
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}$");

    private UUID           userId;
    private String         email;
    private AuthProvider   provider;
    private AccountStatus  status;
    private VisibilityMode visibilityMode;
    private String         passwordHash;
    private String         providerSubject;
    private Instant        createdAt;
    private Instant        lastLoginAt;
    private int            trustScore;
    private int            totalPoints;
    private double         totalDistanceKm;
    private int            completedSessions;
    private String         fcmToken;
    private String         role;

    protected User() {}

    // ── Rehydration constructor (repository → domain) ─────────────────────────

    public User(UUID userId, String email, AuthProvider provider, AccountStatus status,
                VisibilityMode visibilityMode, String passwordHash, String providerSubject,
                Instant createdAt, Instant lastLoginAt,
                int trustScore, int totalPoints, double totalDistanceKm, int completedSessions,
                String fcmToken, String role) {
        this.userId            = userId;
        this.email             = email;
        this.provider          = provider;
        this.status            = status;
        this.visibilityMode    = visibilityMode != null ? visibilityMode : VisibilityMode.PUBLIC;
        this.passwordHash      = passwordHash;
        this.providerSubject   = providerSubject;
        this.createdAt         = createdAt;
        this.lastLoginAt       = lastLoginAt;
        this.trustScore        = trustScore;
        this.totalPoints       = totalPoints;
        this.totalDistanceKm   = totalDistanceKm;
        this.completedSessions = completedSessions;
        this.fcmToken          = fcmToken;
        this.role              = role != null ? role : "USER";
    }

    // ── Creation factory ──────────────────────────────────────────────────────

    private User(String email, String passwordHash) {
        this.email           = normalizeEmail(email);
        this.provider        = DEFAULT_PROVIDER;
        this.status          = DEFAULT_STATUS;
        this.visibilityMode  = VisibilityMode.PUBLIC;
        this.passwordHash    = requireText(passwordHash, "Password hash is required");
        this.providerSubject = null;
        this.lastLoginAt     = null;
        this.createdAt         = Instant.now();
        this.trustScore        = 500;
        this.totalPoints       = 0;
        this.totalDistanceKm   = 0.0;
        this.completedSessions = 0;
        this.fcmToken          = null;
        this.role              = "USER";
    }

    public static User register(String email, String passwordHash) {
        String normalized = normalizeEmail(email);
        if (!EMAIL_PATTERN.matcher(normalized).matches()) {
            throw new DomainException(UserErrorCode.USER_INVALID_EMAIL_FORMAT);
        }
        return new User(normalized, passwordHash);
    }

    /** Factory for brand-new Google Sign-In users (no password). */
    public static User registerWithGoogle(String email, String providerSubject) {
        User u = new User();
        u.email           = normalizeEmail(email);
        u.provider        = AuthProvider.GOOGLE;
        u.status          = DEFAULT_STATUS;
        u.visibilityMode  = VisibilityMode.PUBLIC;
        u.passwordHash    = null;
        u.providerSubject = requireText(providerSubject, "Provider subject is required");
        u.lastLoginAt     = Instant.now();
        u.createdAt         = Instant.now();
        u.trustScore        = 500;
        u.totalPoints       = 0;
        u.totalDistanceKm   = 0.0;
        u.completedSessions = 0;
        u.fcmToken          = null;
        u.role              = "USER";
        return u;
    }



    // ── Domain behaviour ──────────────────────────────────────────────────────

    public static String normalizeEmail(String email) {
        return requireText(email, "Email is required").trim().toLowerCase(Locale.ROOT);
    }

    /** Pure credential check — no side-effects. Call {@link #recordLogin()} afterward. */
    public void validateCredentials(String rawPassword, PasswordMatcher matcher) {
        if (this.status != AccountStatus.ACTIVE) {
            throw new DomainException(UserErrorCode.USER_ACCOUNT_SUSPENDED);
        }
        if (!matcher.matches(rawPassword, this.passwordHash)) {
            throw new DomainException(UserErrorCode.USER_INVALID_CREDENTIALS);
        }
    }

    /** Sets visibility mode with idempotency guard. */
    public void setVisibilityMode(VisibilityMode mode) {
        if (this.visibilityMode == VisibilityMode.PUBLIC && mode == VisibilityMode.PUBLIC) {
            throw new DomainException(UserErrorCode.USER_ALREADY_PUBLIC);
        }
        if (this.visibilityMode == VisibilityMode.PRIVATE && mode == VisibilityMode.PRIVATE) {
            throw new DomainException(UserErrorCode.USER_ALREADY_PRIVATE);
        }
        this.visibilityMode = mode;
    }

    /** Records a successful OAuth login (updates lastLoginAt without a password check). */
    public void recordLogin() {
        this.lastLoginAt = Instant.now();
    }

    /**
     * A2 merge: links this LOCAL account to a Google identity.
     * Throws if a different provider_subject is already set (safety guard).
     */
    public void linkGoogleAccount(String providerSubject) {
        if (this.providerSubject != null && !this.providerSubject.equals(providerSubject)) {
            throw new DomainException(UserErrorCode.USER_PROVIDER_CONFLICT);
        }
        this.providerSubject = requireText(providerSubject, "Provider subject is required");
        this.lastLoginAt     = Instant.now();
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
