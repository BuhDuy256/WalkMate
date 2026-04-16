package com.walkmate.infrastructure.security.oauth;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import com.walkmate.application.user.GoogleIdentity;
import com.walkmate.domain.shared.exception.DomainException;
import com.walkmate.domain.user.UserErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FirebaseTokenVerifierTest {

    @Mock
    private FirebaseAuth firebaseAuth;

    private FirebaseTokenVerifier verifier;

    @BeforeEach
    void setUp() {
        verifier = new FirebaseTokenVerifier(firebaseAuth);
    }

    // ── Happy path ────────────────────────────────────────────────────────────

    @Test
    void verify_validToken_returnsGoogleIdentity() throws FirebaseAuthException {
        FirebaseToken decoded = mock(FirebaseToken.class);
        when(decoded.getUid()).thenReturn("google-sub-123");
        when(decoded.getEmail()).thenReturn("user@example.com");
        when(decoded.getName()).thenReturn("Test User");
        when(decoded.getPicture()).thenReturn("https://example.com/photo.jpg");

        when(firebaseAuth.verifyIdToken(anyString(), anyBoolean())).thenReturn(decoded);

        GoogleIdentity identity = verifier.verify("valid-firebase-id-token");

        assertThat(identity.sub()).isEqualTo("google-sub-123");
        assertThat(identity.email()).isEqualTo("user@example.com");
        assertThat(identity.name()).isEqualTo("Test User");
        assertThat(identity.pictureUrl()).isEqualTo("https://example.com/photo.jpg");
    }

    @Test
    void verify_validToken_withNullNameAndPicture_returnsGoogleIdentity() throws FirebaseAuthException {
        FirebaseToken decoded = mock(FirebaseToken.class);
        when(decoded.getUid()).thenReturn("google-sub-456");
        when(decoded.getEmail()).thenReturn("noname@example.com");
        when(decoded.getName()).thenReturn(null);
        when(decoded.getPicture()).thenReturn(null);

        when(firebaseAuth.verifyIdToken(anyString(), anyBoolean())).thenReturn(decoded);

        GoogleIdentity identity = verifier.verify("valid-firebase-id-token");

        assertThat(identity.sub()).isEqualTo("google-sub-456");
        assertThat(identity.name()).isNull();
        assertThat(identity.pictureUrl()).isNull();
    }

    // ── Expired token ─────────────────────────────────────────────────────────

    @Test
    void verify_expiredToken_throwsDomainExceptionWithInvalidCredentials() throws FirebaseAuthException {
        FirebaseAuthException expiredException = mock(FirebaseAuthException.class);
        when(firebaseAuth.verifyIdToken(anyString(), anyBoolean())).thenThrow(expiredException);

        assertThatThrownBy(() -> verifier.verify("expired-token"))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> {
                    DomainException domainEx = (DomainException) ex;
                    assertThat(domainEx.getErrorCode()).isEqualTo(UserErrorCode.USER_INVALID_CREDENTIALS);
                });
    }

    // ── Revoked token ─────────────────────────────────────────────────────────

    @Test
    void verify_revokedToken_throwsDomainExceptionWithInvalidCredentials() throws FirebaseAuthException {
        FirebaseAuthException revokedException = mock(FirebaseAuthException.class);
        when(firebaseAuth.verifyIdToken(anyString(), anyBoolean())).thenThrow(revokedException);

        assertThatThrownBy(() -> verifier.verify("revoked-token"))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> {
                    DomainException domainEx = (DomainException) ex;
                    assertThat(domainEx.getErrorCode()).isEqualTo(UserErrorCode.USER_INVALID_CREDENTIALS);
                });
    }
}
