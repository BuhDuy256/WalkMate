package com.walkmate.application.user;

import com.walkmate.domain.shared.exception.DomainException;
import com.walkmate.domain.user.AccountStatus;
import com.walkmate.domain.user.AuthProvider;
import com.walkmate.domain.user.OtpRecord;
import com.walkmate.domain.user.OtpRecordRepository;
import com.walkmate.domain.user.RefreshToken;
import com.walkmate.domain.user.RefreshTokenRepository;
import com.walkmate.domain.user.User;
import com.walkmate.domain.user.UserErrorCode;
import com.walkmate.domain.user.UserProfile;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the phone OTP flow in {@link UserCommandService}.
 *
 * sendOtp  — happy path, rate-limited (fresh unused OTP), already-used OTP allows resend.
 * verifyOtp — new user auto-registered, existing user re-login, no OTP record found.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PhoneOtpServiceTest {

    private static final String PHONE = "+84901234567";

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
        when(passwordEncoder.encode(anyString())).thenReturn("hashed-code");
        when(tokenProvider.generateTokenPair(any()))
                .thenReturn(new TokenPair("access", 3600L, "refresh", 2592000L));
    }

    // ── sendOtp — happy path ──────────────────────────────────────────────────

    @Test
    void sendOtp_noExistingOtp_savesRecordAndSendsSms() {
        when(otpRecordRepository.findLatestByPhone(PHONE)).thenReturn(Optional.empty());

        service.sendOtp(new SendOtpCommand(PHONE));

        ArgumentCaptor<OtpRecord> captor = ArgumentCaptor.forClass(OtpRecord.class);
        verify(otpRecordRepository).save(captor.capture());
        assertThat(captor.getValue().isUsed()).isFalse();
        assertThat(captor.getValue().getPhone()).isEqualTo(PHONE);

        verify(smsGateway).send(anyString(), anyString());
    }

    @Test
    void sendOtp_oldUsedOtp_allowsResend() {
        // An already-used OTP should never block a new send
        OtpRecord usedOtp = new OtpRecord(UUID.randomUUID(), PHONE, "hash",
                Instant.now().plusSeconds(300), true, 1);
        when(otpRecordRepository.findLatestByPhone(PHONE)).thenReturn(Optional.of(usedOtp));

        service.sendOtp(new SendOtpCommand(PHONE));

        verify(smsGateway).send(anyString(), anyString());
    }

    @Test
    void sendOtp_freshUnusedOtp_throwsRateLimited() {
        // OTP issued 10s ago (expiresAt - 300s = issuedAt; now < expiresAt - 240 → rate limited)
        OtpRecord freshOtp = new OtpRecord(UUID.randomUUID(), PHONE, "hash",
                Instant.now().plusSeconds(290), false, 0);  // issued ~10s ago
        when(otpRecordRepository.findLatestByPhone(PHONE)).thenReturn(Optional.of(freshOtp));

        assertThatThrownBy(() -> service.sendOtp(new SendOtpCommand(PHONE)))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getErrorCode())
                        .isEqualTo(UserErrorCode.USER_OTP_RATE_LIMITED));

        verify(smsGateway, never()).send(anyString(), anyString());
    }

    @Test
    void sendOtp_expiredUnusedOtp_allowsResend() {
        // OTP expired more than 60s ago — rate limit should NOT apply
        OtpRecord expiredOtp = new OtpRecord(UUID.randomUUID(), PHONE, "hash",
                Instant.now().minusSeconds(60), false, 0);
        when(otpRecordRepository.findLatestByPhone(PHONE)).thenReturn(Optional.of(expiredOtp));

        service.sendOtp(new SendOtpCommand(PHONE));

        verify(smsGateway).send(anyString(), anyString());
    }

    // ── verifyOtp — new user auto-registration ────────────────────────────────

    @Test
    void verifyOtp_newPhone_registersUserAndProfile() {
        OtpRecord record = freshOtp();
        when(otpRecordRepository.findLatestByPhone(PHONE)).thenReturn(Optional.of(record));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);

        // No existing user → create new
        when(userRepository.findByPhone(PHONE)).thenReturn(Optional.empty());
        User newUser = buildPhoneUser();
        when(userRepository.save(any(User.class))).thenReturn(newUser);

        LoginResult result = service.verifyOtp(new VerifyOtpCommand(PHONE, "123456", "device-1"));

        assertThat(result.accessToken()).isEqualTo("access");
        assertThat(result.refreshToken()).isEqualTo("refresh");

        verify(profileRepository).save(any(UserProfile.class));
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    // ── verifyOtp — existing user re-login ────────────────────────────────────

    @Test
    void verifyOtp_existingPhone_doesNotCreateNewUser() {
        OtpRecord record = freshOtp();
        when(otpRecordRepository.findLatestByPhone(PHONE)).thenReturn(Optional.of(record));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);

        User existingUser = buildPhoneUser();
        when(userRepository.findByPhone(PHONE)).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(User.class))).thenReturn(existingUser);

        LoginResult result = service.verifyOtp(new VerifyOtpCommand(PHONE, "123456", "device-1"));

        assertThat(result.accessToken()).isEqualTo("access");
        // No new user row, no profile row
        verify(profileRepository, never()).save(any(UserProfile.class));
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    // ── verifyOtp — no OTP record ─────────────────────────────────────────────

    @Test
    void verifyOtp_noOtpRecord_throwsDomainException() {
        when(otpRecordRepository.findLatestByPhone(PHONE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verifyOtp(new VerifyOtpCommand(PHONE, "123456", "device-1")))
                .isInstanceOf(DomainException.class);

        verify(tokenProvider, never()).generateTokenPair(any());
    }

    // ── verifyOtp — suspended account ────────────────────────────────────────

    @Test
    void verifyOtp_suspendedAccount_throwsAccountSuspended() {
        OtpRecord record = freshOtp();
        when(otpRecordRepository.findLatestByPhone(PHONE)).thenReturn(Optional.of(record));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);

        User suspended = new User(UUID.randomUUID(), null, PHONE,
                AuthProvider.PHONE, AccountStatus.SUSPENDED, VisibilityMode.PUBLIC,
                null, null, Instant.now(), null, 0, 0, 0.0, 0, null);
        when(userRepository.findByPhone(PHONE)).thenReturn(Optional.of(suspended));

        assertThatThrownBy(() -> service.verifyOtp(new VerifyOtpCommand(PHONE, "123456", "device-1")))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getErrorCode())
                        .isEqualTo(UserErrorCode.USER_ACCOUNT_SUSPENDED));

        verify(tokenProvider, never()).generateTokenPair(any());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private OtpRecord freshOtp() {
        return new OtpRecord(UUID.randomUUID(), PHONE, "hashed-code",
                Instant.now().plusSeconds(300), false, 0);
    }

    private User buildPhoneUser() {
        return new User(UUID.randomUUID(), null, PHONE,
                AuthProvider.PHONE, AccountStatus.ACTIVE, VisibilityMode.PUBLIC,
                null, null, Instant.now(), null, 0, 0, 0.0, 0, null);
    }
}
