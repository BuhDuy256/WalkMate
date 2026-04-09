package com.walkmate.application.user;

import com.walkmate.domain.user.OtpRecordRepository;
import com.walkmate.domain.user.RefreshTokenRepository;
import com.walkmate.domain.user.UserProfileRepository;
import com.walkmate.domain.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.UUID;

import static org.mockito.Mockito.verify;

/**
 * Unit tests for logout flows in {@link UserCommandService}.
 * Covers: single-device logout and logout-all (revoke all sessions).
 */
@ExtendWith(MockitoExtension.class)
class LogoutServiceTest {

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
    }

    // ── Single-device logout ──────────────────────────────────────────────────

    @Test
    void logout_validUserAndDevice_deletesOnlyThatDeviceToken() {
        UUID userId   = UUID.randomUUID();
        String device = "device-abc";

        service.logout(userId, device);

        verify(refreshTokenRepository).deleteByUserIdAndDeviceId(userId, device);
    }

    @Test
    void logout_differentDeviceIds_eachDeletesItsOwnToken() {
        UUID userId    = UUID.randomUUID();
        String deviceA = "device-A";
        String deviceB = "device-B";

        service.logout(userId, deviceA);
        service.logout(userId, deviceB);

        verify(refreshTokenRepository).deleteByUserIdAndDeviceId(userId, deviceA);
        verify(refreshTokenRepository).deleteByUserIdAndDeviceId(userId, deviceB);
    }

    // ── Logout-all ────────────────────────────────────────────────────────────

    @Test
    void logoutAll_validUser_deletesAllUserSessions() {
        UUID userId = UUID.randomUUID();

        service.logoutAll(userId);

        verify(refreshTokenRepository).deleteAllByUserId(userId);
    }

    @Test
    void logoutAll_doesNotTouchOtherUsers() {
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();

        service.logoutAll(userA);

        verify(refreshTokenRepository).deleteAllByUserId(userA);
        // userB's tokens must not be touched
        verify(refreshTokenRepository, org.mockito.Mockito.never()).deleteAllByUserId(userB);
    }
}
