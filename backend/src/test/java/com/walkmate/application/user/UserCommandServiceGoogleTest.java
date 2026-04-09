package com.walkmate.application.user;

import com.walkmate.domain.shared.exception.DomainException;
import com.walkmate.domain.user.AccountStatus;
import com.walkmate.domain.user.AuthProvider;
import com.walkmate.domain.user.RefreshToken;
import com.walkmate.domain.user.RefreshTokenRepository;
import com.walkmate.domain.user.User;
import com.walkmate.domain.user.UserErrorCode;
import com.walkmate.domain.user.UserProfile;
import com.walkmate.domain.user.UserProfileRepository;
import com.walkmate.domain.user.UserRepository;
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
 * Unit tests for the Google Sign-In flow in {@link UserCommandService}.
 *
 * Covers all three business scenarios:
 *  1. Brand-new Google user → account + profile auto-created.
 *  2. Existing LOCAL user with same email → A2 merge (linkGoogleAccount).
 *  3. Existing Google user (by providerSubject) → idempotent re-login.
 *  4. Invalid / expired Firebase token → DomainException propagated.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserCommandServiceGoogleTest {

    @Mock private UserRepository userRepository;
    @Mock private UserProfileRepository profileRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private com.walkmate.domain.user.OtpRecordRepository otpRecordRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private TokenProvider tokenProvider;
    @Mock private GoogleTokenVerifier googleTokenVerifier;
    @Mock private SmsGateway smsGateway;

    private UserCommandService service;

    private static final String FIREBASE_ID_TOKEN = "firebase-id-token";
    private static final String GOOGLE_SUB        = "google-sub-abc123";
    private static final String USER_EMAIL        = "user@example.com";
    private static final String USER_NAME         = "Test User";
    private static final String PICTURE_URL       = "https://example.com/pic.jpg";

    private final GoogleIdentity identity =
            new GoogleIdentity(GOOGLE_SUB, USER_EMAIL, USER_NAME, PICTURE_URL);

    @BeforeEach
    void setUp() {
        service = new UserCommandService(
                userRepository,
                profileRepository,
                refreshTokenRepository,
                otpRecordRepository,
                passwordEncoder,
                tokenProvider,
                googleTokenVerifier,
                smsGateway
        );
        when(tokenProvider.generateTokenPair(any())).thenReturn(new TokenPair("access-token", 3600L, "refresh-token", 2592000L));
    }

    // ── Scenario 1: Brand-new Google user ────────────────────────────────────

    @Test
    void loginOrRegisterWithGoogle_newUser_createsAccountAndProfile() {
        when(googleTokenVerifier.verify(FIREBASE_ID_TOKEN)).thenReturn(identity);
        when(userRepository.findByProviderSubject(GOOGLE_SUB)).thenReturn(Optional.empty());
        when(userRepository.findByEmail(USER_EMAIL)).thenReturn(Optional.empty());

        // save() is called twice: once in resolveByEmailOrCreate (gets the UUID),
        // once after recordLogin(). Both return the same fully-initialized user.
        User savedUser = buildSavedGoogleUser();
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        LoginResult result = service.loginOrRegisterWithGoogle(new GoogleAuthCommand(FIREBASE_ID_TOKEN, "device-test"));

        assertThat(result.accessToken()).isEqualTo("access-token");

        // Profile must be auto-created for new Google users
        ArgumentCaptor<UserProfile> profileCaptor = ArgumentCaptor.forClass(UserProfile.class);
        verify(profileRepository).save(profileCaptor.capture());
        assertThat(profileCaptor.getValue().getFullName()).isEqualTo(USER_NAME);

        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    // ── Scenario 2: A2 Merge — LOCAL user with same email ────────────────────

    @Test
    void loginOrRegisterWithGoogle_localUserSameEmail_linksGoogleAccount() {
        when(googleTokenVerifier.verify(FIREBASE_ID_TOKEN)).thenReturn(identity);
        when(userRepository.findByProviderSubject(GOOGLE_SUB)).thenReturn(Optional.empty());

        User localUser = buildLocalUser();
        when(userRepository.findByEmail(USER_EMAIL)).thenReturn(Optional.of(localUser));
        when(userRepository.save(any(User.class))).thenReturn(localUser);

        LoginResult result = service.loginOrRegisterWithGoogle(new GoogleAuthCommand(FIREBASE_ID_TOKEN, "device-test"));

        assertThat(result.accessToken()).isEqualTo("access-token");
        // providerSubject should now be set (linkGoogleAccount was called)
        assertThat(localUser.getProviderSubject()).isEqualTo(GOOGLE_SUB);
        // No new profile row should be created for an existing user
        verify(profileRepository, never()).save(any(UserProfile.class));
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    // ── Scenario 3: Idempotent re-login (existing Google user) ───────────────

    @Test
    void loginOrRegisterWithGoogle_existingGoogleUser_idempotentLogin() {
        when(googleTokenVerifier.verify(FIREBASE_ID_TOKEN)).thenReturn(identity);

        User existingGoogleUser = buildSavedGoogleUser();
        when(userRepository.findByProviderSubject(GOOGLE_SUB))
                .thenReturn(Optional.of(existingGoogleUser));
        when(userRepository.save(any(User.class))).thenReturn(existingGoogleUser);

        LoginResult result = service.loginOrRegisterWithGoogle(new GoogleAuthCommand(FIREBASE_ID_TOKEN, "device-test"));

        assertThat(result.accessToken()).isEqualTo("access-token");
        // No email lookup or profile creation needed
        verify(userRepository, never()).findByEmail(anyString());
        verify(profileRepository, never()).save(any(UserProfile.class));
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    // ── Scenario 4: Invalid Firebase token ───────────────────────────────────

    @Test
    void loginOrRegisterWithGoogle_invalidToken_throwsDomainException() {
        when(googleTokenVerifier.verify(FIREBASE_ID_TOKEN))
                .thenThrow(new DomainException(UserErrorCode.USER_INVALID_CREDENTIALS));

        assertThatThrownBy(() ->
                service.loginOrRegisterWithGoogle(new GoogleAuthCommand(FIREBASE_ID_TOKEN, "device-test")))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getErrorCode())
                        .isEqualTo(UserErrorCode.USER_INVALID_CREDENTIALS));

        verify(userRepository, never()).save(any());
        verify(profileRepository, never()).save(any());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Google user that already has a persisted UUID (as it would after repository.save()). */
    private User buildSavedGoogleUser() {
        return new User(
                UUID.randomUUID(), USER_EMAIL, null, AuthProvider.GOOGLE, AccountStatus.ACTIVE,
                com.walkmate.domain.user.VisibilityMode.PUBLIC,
                null, GOOGLE_SUB, Instant.now(), Instant.now(),
                0, 0, 0.0, 0, null
        );
    }

    /** LOCAL user with a real UUID but no providerSubject. */
    private User buildLocalUser() {
        return new User(
                UUID.randomUUID(), USER_EMAIL, null, AuthProvider.LOCAL, AccountStatus.ACTIVE,
                com.walkmate.domain.user.VisibilityMode.PUBLIC,
                "$2a$10$hashedpassword", null, Instant.now(), null,
                0, 0, 0.0, 0, null
        );
    }
}
