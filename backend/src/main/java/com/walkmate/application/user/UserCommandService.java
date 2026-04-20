package com.walkmate.application.user;

import com.walkmate.domain.shared.exception.DomainException;
import com.walkmate.domain.user.AccountStatus;
import com.walkmate.domain.user.AccountStatus;
import com.walkmate.domain.user.RefreshToken;
import com.walkmate.domain.user.RefreshTokenRepository;
import com.walkmate.domain.user.User;
import com.walkmate.domain.user.UserErrorCode;
import com.walkmate.domain.user.UserProfile;
import com.walkmate.domain.user.UserProfileRepository;
import com.walkmate.domain.user.UserRepository;
import com.walkmate.domain.user.VisibilityMode;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserCommandService {

    private final UserRepository         userRepository;
    private final UserProfileRepository  profileRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder        passwordEncoder;
    private final TokenProvider          tokenProvider;
    private final GoogleTokenVerifier    googleTokenVerifier;

    private final SecureRandom secureRandom = new SecureRandom();

    // ── Login / Register ──────────────────────────────────────────────────────

    @Transactional
    public LoginResult loginUser(LoginUserCommand command) {
        String normalizedEmail = User.normalizeEmail(command.email());

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new DomainException(UserErrorCode.USER_INVALID_CREDENTIALS));

        user.validateCredentials(command.password(), passwordEncoder::matches);
        user.recordLogin();

        TokenPair tokenPair    = tokenProvider.generateTokenPair(user);
        Instant   refreshExpiry = Instant.now().plusSeconds(tokenPair.refreshTokenExpiresIn());

        refreshTokenRepository.save(
                RefreshToken.issue(user.getUserId(), command.deviceId(), tokenPair.refreshToken(), refreshExpiry));

        userRepository.save(user);

        return new LoginResult(tokenPair.accessToken(), tokenPair.accessTokenExpiresIn(),
                tokenPair.refreshToken(), tokenPair.refreshTokenExpiresIn());
    }

    /**
     * Google Sign-In — find-or-create with A2 merge.
     *
     * Flow:
     * 1. Verify Firebase ID token → extract GoogleIdentity (sub, email, name, pictureUrl).
     * 2. Look up by provider_subject  → existing Google user  → recordLogin + issue JWT.
     * 3. Look up by email             → existing LOCAL user   → linkGoogleAccount (A2 merge) + issue JWT.
     * 4. Neither found               → create new GOOGLE user + auto-create profile + issue JWT.
     */
    @Transactional
    public LoginResult loginOrRegisterWithGoogle(GoogleAuthCommand command) {
        GoogleIdentity identity = googleTokenVerifier.verify(command.firebaseIdToken());

        // ── 1. Existing Google user ───────────────────────────────────────────
        User user = userRepository.findByProviderSubject(identity.sub())
                .orElseGet(() -> resolveByEmailOrCreate(identity));

        user.recordLogin();
        userRepository.save(user);

        TokenPair tokenPair    = tokenProvider.generateTokenPair(user);
        Instant   refreshExpiry = Instant.now().plusSeconds(tokenPair.refreshTokenExpiresIn());

        refreshTokenRepository.save(
                RefreshToken.issue(user.getUserId(), command.deviceId(), tokenPair.refreshToken(), refreshExpiry));

        return new LoginResult(tokenPair.accessToken(), tokenPair.accessTokenExpiresIn(),
                tokenPair.refreshToken(), tokenPair.refreshTokenExpiresIn());
    }

    @Transactional
    public LoginResult registerUser(RegisterUserCommand command) {
        String normalizedEmail = User.normalizeEmail(command.email());

        userRepository.findByEmail(normalizedEmail)
                .ifPresent(existing -> {
                    throw new DomainException(UserErrorCode.USER_EMAIL_ALREADY_EXISTS);
                });

        User user  = User.register(normalizedEmail, passwordEncoder.encode(command.password()));
        User saved = userRepository.save(user);
        profileRepository.save(UserProfile.createForLocal(saved.getUserId(), command.fullName()));

        TokenPair tokenPair    = tokenProvider.generateTokenPair(saved);
        Instant   refreshExpiry = Instant.now().plusSeconds(tokenPair.refreshTokenExpiresIn());
        refreshTokenRepository.save(
                RefreshToken.issue(saved.getUserId(), command.deviceId(), tokenPair.refreshToken(), refreshExpiry));

        return new LoginResult(tokenPair.accessToken(), tokenPair.accessTokenExpiresIn(),
                tokenPair.refreshToken(), tokenPair.refreshTokenExpiresIn());
    }

    // ── Token rotation ────────────────────────────────────────────────────────

    @Transactional
    public LoginResult refreshToken(String tokenValue) {
        RefreshToken existing = refreshTokenRepository.findByTokenValue(tokenValue)
                .orElseThrow(() -> new DomainException(UserErrorCode.INVALID_USER_DATA,
                        "Refresh token not found"));

        // Reuse detection: a previously rotated token being re-presented is a compromise signal.
        if (existing.isRevoked()) {
            refreshTokenRepository.deleteAllByUserId(existing.getUserId());
            throw new DomainException(UserErrorCode.INVALID_USER_DATA,
                    "Refresh token reuse detected — all sessions revoked");
        }

        if (Instant.now().isAfter(existing.getExpiresAt())) {
            refreshTokenRepository.deleteByUserIdAndDeviceId(existing.getUserId(), existing.getDeviceId());
            throw new DomainException(UserErrorCode.INVALID_USER_DATA, "Refresh token has expired");
        }

        User user = userRepository.findById(existing.getUserId().toString())
                .orElseThrow(() -> new DomainException(UserErrorCode.USER_NOT_FOUND));

        // Rotate: mark old token revoked (keeps it detectable), issue new token.
        existing.revoke();
        refreshTokenRepository.save(existing);

        TokenPair tokenPair    = tokenProvider.generateTokenPair(user);
        Instant   refreshExpiry = Instant.now().plusSeconds(tokenPair.refreshTokenExpiresIn());
        refreshTokenRepository.save(
                RefreshToken.issue(user.getUserId(), existing.getDeviceId(), tokenPair.refreshToken(), refreshExpiry));

        user.recordLogin();
        userRepository.save(user);

        return new LoginResult(tokenPair.accessToken(), tokenPair.accessTokenExpiresIn(),
                tokenPair.refreshToken(), tokenPair.refreshTokenExpiresIn());
    }

    // ── Logout ────────────────────────────────────────────────────────────────

    @Transactional
    public void logout(UUID userId, String deviceId) {
        refreshTokenRepository.deleteByUserIdAndDeviceId(userId, deviceId);
    }

    @Transactional
    public void logoutAll(UUID userId) {
        refreshTokenRepository.deleteAllByUserId(userId);
    }

    // ── Visibility ────────────────────────────────────────────────────────────

    @Transactional
    public VisibilityMode setVisibilityMode(SetVisibilityCommand command) {
        User user = userRepository.findById(command.userId().toString())
                .orElseThrow(() -> new DomainException(UserErrorCode.USER_NOT_FOUND));
        user.setVisibilityMode(command.mode());
        userRepository.save(user);
        return user.getVisibilityMode();
    }



    // ── FCM ───────────────────────────────────────────────────────────────────

    @Transactional
    public void updateFcmToken(UpdateFcmTokenCommand command) {
        userRepository.updateFcmToken(command.userId(), command.token());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Called when no user owns the Google sub claim yet.
     * Either merges into an existing LOCAL account (A2) or creates a brand-new one.
     */
    private User resolveByEmailOrCreate(GoogleIdentity identity) {
        return userRepository.findByEmail(User.normalizeEmail(identity.email()))
                .map(existing -> {
                    // A2: LOCAL account with same email — link the Google identity
                    existing.linkGoogleAccount(identity.sub());
                    return existing;
                })
                .orElseGet(() -> {
                    // Brand-new Google user
                    User newUser = User.registerWithGoogle(identity.email(), identity.sub());
                    User saved   = userRepository.save(newUser);
                    // Auto-create profile from Google token claims
                    profileRepository.save(
                            UserProfile.createForOAuth(saved.getUserId(), identity.name(), identity.pictureUrl())
                    );
                    return saved;
                });
    }
}
