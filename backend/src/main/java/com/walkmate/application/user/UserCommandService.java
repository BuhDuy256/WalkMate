package com.walkmate.application.user;

import com.walkmate.domain.shared.exception.DomainException;
import com.walkmate.domain.user.AccountStatus;
import com.walkmate.domain.user.OtpRecord;
import com.walkmate.domain.user.OtpRecordRepository;
import com.walkmate.domain.user.Phone;
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
    private final OtpRecordRepository    otpRecordRepository;
    private final PasswordEncoder        passwordEncoder;
    private final TokenProvider          tokenProvider;
    private final GoogleTokenVerifier    googleTokenVerifier;
    private final SmsGateway             smsGateway;

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

        // Rotate: mark old token revoked (keeps it detectable for reuse detection), issue new token.
        // Uses a direct UPDATE rather than the UPSERT save() — the UPSERT ON CONFLICT predicate
        // (WHERE revoked = false) is only evaluated against the NEW row being inserted; a row
        // with revoked=true would bypass the conflict target and hit the primary key constraint.
        refreshTokenRepository.revokeById(existing.getTokenId());

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

    // ── Phone OTP ─────────────────────────────────────────────────────────────

    @Transactional
    public void sendOtp(SendOtpCommand command) {
        Phone phone = new Phone(command.phone());

        // Rate-limit: block resend if a non-used OTP was issued less than 60 seconds ago.
        // expiresAt = issuedAt + 300s, so issuedAt = expiresAt - 300s.
        // "Less than 60s ago" ↔ now < issuedAt + 60 ↔ now < expiresAt - 240.
        otpRecordRepository.findLatestByPhone(phone.value()).ifPresent(existing -> {
            if (!existing.isUsed() && Instant.now().isBefore(existing.getExpiresAt().minusSeconds(240))) {
                throw new DomainException(UserErrorCode.USER_OTP_RATE_LIMITED);
            }
        });

        String  rawCode   = String.format("%06d", secureRandom.nextInt(1_000_000));
        String  codeHash  = passwordEncoder.encode(rawCode);
        Instant expiresAt = Instant.now().plusSeconds(300); // 5 minutes

        otpRecordRepository.deleteByPhone(phone.value());
        otpRecordRepository.save(OtpRecord.issue(phone.value(), codeHash, expiresAt));

        smsGateway.send(phone.value(), "Your WalkMate OTP is: " + rawCode);
    }

    @Transactional
    public LoginResult verifyOtp(VerifyOtpCommand command) {
        Phone phone = new Phone(command.phone());

        OtpRecord record = otpRecordRepository.findLatestByPhone(phone.value())
                .orElseThrow(() -> new DomainException(UserErrorCode.USER_OTP_EXPIRED));

        record.verify(command.code(), passwordEncoder::matches);
        otpRecordRepository.save(record);

        User user = userRepository.findByPhone(phone.value())
                .orElseGet(() -> {
                    User newUser = User.registerWithPhone(phone);
                    User saved   = userRepository.save(newUser);
                    profileRepository.save(UserProfile.createBlank(saved.getUserId()));
                    return saved;
                });

        if (user.getStatus() != AccountStatus.ACTIVE) {
            throw new DomainException(UserErrorCode.USER_ACCOUNT_SUSPENDED);
        }

        user.recordLogin();
        userRepository.save(user);

        TokenPair tokenPair    = tokenProvider.generateTokenPair(user);
        Instant   refreshExpiry = Instant.now().plusSeconds(tokenPair.refreshTokenExpiresIn());
        refreshTokenRepository.save(
                RefreshToken.issue(user.getUserId(), command.deviceId(), tokenPair.refreshToken(), refreshExpiry));

        return new LoginResult(tokenPair.accessToken(), tokenPair.accessTokenExpiresIn(),
                tokenPair.refreshToken(), tokenPair.refreshTokenExpiresIn());
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
