package com.walkmate.application.user;

import com.walkmate.domain.shared.exception.DomainException;
import com.walkmate.domain.user.AccountStatus;
import com.walkmate.domain.user.AuthProvider;
import com.walkmate.domain.user.OtpRecordRepository;
import com.walkmate.domain.user.RefreshToken;
import com.walkmate.domain.user.RefreshTokenRepository;
import com.walkmate.domain.user.User;
import com.walkmate.domain.user.UserProfileRepository;
import com.walkmate.domain.user.UserRepository;
import com.walkmate.domain.user.VisibilityMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the token refresh flow in {@link UserCommandService}.
 * Covers: happy-path rotation, expired token, reuse detection (compromise signal).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TokenRefreshServiceTest {

    @Mock private UserRepository         userRepository;
    @Mock private UserProfileRepository  profileRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private OtpRecordRepository    otpRecordRepository;
    @Mock private PasswordEncoder        passwordEncoder;
    @Mock private TokenProvider          tokenProvider;
    @Mock private GoogleTokenVerifier    googleTokenVerifier;
    @Mock private SmsGateway             smsGateway;

    private UserCommandService service;

    @BeforeEach
    void setUp() {
        service = new UserCommandService(
                userRepository, profileRepository, refreshTokenRepository,
                otpRecordRepository, passwordEncoder, tokenProvider, googleTokenVerifier, smsGateway
        );
        when(tokenProvider.generateTokenPair(any()))
                .thenReturn(new TokenPair("new-access", 3600L, "new-refresh", 2592000L));
    }

    // ── Happy path ────────────────────────────────────────────────────────────

    @Test
    void refreshToken_validToken_returnsNewLoginResultAndRevokesOldToken() {
        UUID userId = UUID.randomUUID();
        RefreshToken existing = activeToken(userId, "valid-token");
        User user = buildUser(userId);

        when(refreshTokenRepository.findByTokenValue("valid-token")).thenReturn(Optional.of(existing));
        when(userRepository.findById(userId.toString())).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenReturn(user);

        LoginResult result = service.refreshToken("valid-token");

        assertThat(result.accessToken()).isEqualTo("new-access");
        assertThat(result.refreshToken()).isEqualTo("new-refresh");

        // Two save calls: (1) old token marked revoked, (2) new token issued
        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues().get(0).isRevoked()).isTrue();  // old token → revoked
        assertThat(captor.getAllValues().get(1).isRevoked()).isFalse(); // new token → active
    }

    // ── Token not found ───────────────────────────────────────────────────────

    @Test
    void refreshToken_tokenNotFound_throwsDomainException() {
        when(refreshTokenRepository.findByTokenValue(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.refreshToken("unknown-token"))
                .isInstanceOf(DomainException.class);

        verify(tokenProvider, never()).generateTokenPair(any());
    }

    // ── Expired token ─────────────────────────────────────────────────────────

    @Test
    void refreshToken_expiredToken_deletesTokenAndThrows() {
        UUID userId = UUID.randomUUID();
        RefreshToken expired = expiredToken(userId, "expired-token");

        when(refreshTokenRepository.findByTokenValue("expired-token")).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.refreshToken("expired-token"))
                .isInstanceOf(DomainException.class);

        verify(refreshTokenRepository).deleteByUserIdAndDeviceId(userId, "device-1");
        verify(tokenProvider, never()).generateTokenPair(any());
    }

    // ── Reuse detection (security) ────────────────────────────────────────────

    @Test
    void refreshToken_revokedToken_revokesAllUserSessionsAndThrows() {
        UUID userId = UUID.randomUUID();
        RefreshToken revoked = revokedToken(userId, "rotated-token");

        when(refreshTokenRepository.findByTokenValue("rotated-token")).thenReturn(Optional.of(revoked));

        assertThatThrownBy(() -> service.refreshToken("rotated-token"))
                .isInstanceOf(DomainException.class);

        // Compromise signal: nuke ALL tokens for this user
        verify(refreshTokenRepository).deleteAllByUserId(userId);
        // Must NOT issue a new token
        verify(tokenProvider, never()).generateTokenPair(any());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private RefreshToken activeToken(UUID userId, String tokenValue) {
        return new RefreshToken(UUID.randomUUID(), userId, "device-1", tokenValue,
                Instant.now().minusSeconds(60), Instant.now().plusSeconds(3600), false);
    }

    private RefreshToken expiredToken(UUID userId, String tokenValue) {
        return new RefreshToken(UUID.randomUUID(), userId, "device-1", tokenValue,
                Instant.now().minusSeconds(7200), Instant.now().minusSeconds(60), false);
    }

    private RefreshToken revokedToken(UUID userId, String tokenValue) {
        return new RefreshToken(UUID.randomUUID(), userId, "device-1", tokenValue,
                Instant.now().minusSeconds(3600), Instant.now().plusSeconds(3600), true);
    }

    private User buildUser(UUID userId) {
        return new User(userId, "user@example.com", null,
                AuthProvider.LOCAL, AccountStatus.ACTIVE, VisibilityMode.PUBLIC,
                "hashed-pw", null, Instant.now(), null,
                0, 0, 0.0, 0, null);
    }
}
