package com.walkmate.integration.auth;

import com.walkmate.application.user.GoogleIdentity;
import com.walkmate.domain.shared.exception.DomainException;
import com.walkmate.domain.user.UserErrorCode;
import com.walkmate.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for UC-07 — Login with Google (OAuth).
 *
 * <h3>Tasks covered</h3>
 * <ul>
 *   <li>T07-1 — New Google user: first-time sign-in creates a WalkMate account</li>
 *   <li>T07-2 — Existing LOCAL user: same email linked, no duplicate account created</li>
 *   <li>T07-3 — Invalid Firebase token: verifier throws → HTTP 400 USER_INVALID_CREDENTIALS</li>
 * </ul>
 *
 * <h3>Stub strategy</h3>
 * <p>{@code googleTokenVerifier} is a {@code @MockitoBean} declared in
 * {@link AbstractIntegrationTest}. Each test stubs {@code verify(idToken)} to return
 * a controlled {@link GoogleIdentity} (happy path) or throw a
 * {@link DomainException} (error path).  No real Firebase Admin SDK call is ever made.
 *
 * <h3>Key correction vs. use-case doc</h3>
 * <p>An invalid token causes {@code FirebaseTokenVerifier.verify()} to throw
 * {@code DomainException(USER_INVALID_CREDENTIALS)}, not {@code GOOGLE_LOGIN_FAILED}.
 * The {@code GlobalExceptionHandler} maps this to HTTP 400.
 */
class OAuthIntegrationTest extends AbstractIntegrationTest {

    private static final String GOOGLE_URL   = "/api/v1/auth/google";
    private static final String REGISTER_URL = "/api/v1/auth/register";

    private static final String FAKE_TOKEN  = "fake-firebase-id-token";
    private static final String DEVICE_ID   = "test-device-oauth";

    // ── T07-1: Google OAuth — New User Registration ───────────────────────────

    @Test
    void t07_1_googleLogin_newUser_returns200WithTokenPair() throws Exception {
        // Arrange — stub verifier to return a fresh Google identity (no prior WalkMate account)
        when(googleTokenVerifier.verify(FAKE_TOKEN)).thenReturn(new GoogleIdentity(
                "google-sub-new-001",
                "newuser@gmail.com",
                "New User",
                "https://lh3.googleusercontent.com/photo.jpg"
        ));

        // Act + Assert
        mockMvc.perform(post(GOOGLE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(googleBody(FAKE_TOKEN, DEVICE_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isString())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresIn", notNullValue()));
    }

    // ── T07-2: Google OAuth — Existing LOCAL User Account Linking ─────────────

    @Test
    void t07_2_googleLogin_existingLocalUser_sameEmail_returns200_noUserDuplicated() throws Exception {
        // Arrange — register a LOCAL user first (email+password path)
        String sharedEmail = "existing@example.com";
        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fullname", "Existing User",
                                "email",    sharedEmail,
                                "password", "Password1!",
                                "deviceId", "device-local"
                        ))))
                .andExpect(status().isCreated());

        // Stub Google verifier to return the SAME email as the local account
        when(googleTokenVerifier.verify(FAKE_TOKEN)).thenReturn(new GoogleIdentity(
                "google-sub-link-002",
                sharedEmail,
                "Existing User",
                null
        ));

        // Act — Google sign-in with the same email must succeed (account linking, not duplication)
        mockMvc.perform(post(GOOGLE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(googleBody(FAKE_TOKEN, DEVICE_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());

        // Assert — only ONE user_account row for this email exists in DB
        int count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public.user_account WHERE email = ?",
                Integer.class,
                sharedEmail
        );
        org.assertj.core.api.Assertions.assertThat(count).isEqualTo(1);
    }

    // ── T07-3: Google OAuth — Invalid Firebase Token ──────────────────────────

    @Test
    void t07_3_googleLogin_invalidToken_returns400_USER_INVALID_CREDENTIALS() throws Exception {
        // Arrange — stub verifier to throw DomainException (mirrors FirebaseTokenVerifier behaviour
        // when Firebase rejects the token with FirebaseAuthException)
        when(googleTokenVerifier.verify(FAKE_TOKEN))
                .thenThrow(new DomainException(UserErrorCode.USER_INVALID_CREDENTIALS));

        // Act + Assert
        mockMvc.perform(post(GOOGLE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(googleBody(FAKE_TOKEN, DEVICE_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("USER_INVALID_CREDENTIALS"));
    }

    // ── Payload builder ────────────────────────────────────────────────────────

    private String googleBody(String idToken, String deviceId) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "idToken",  idToken,
                "deviceId", deviceId
        ));
    }
}
