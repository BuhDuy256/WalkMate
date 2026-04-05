package com.walkmate.presentation.controller.user;

import com.walkmate.application.user.LoginResult;
import com.walkmate.application.user.UserCommandService;
import com.walkmate.domain.shared.exception.DomainException;
import com.walkmate.domain.user.UserErrorCode;
import com.walkmate.infrastructure.config.SecurityConfig;
import com.walkmate.presentation.dto.response.user.LoginUserResponse;
import com.walkmate.presentation.exception.GlobalExceptionHandler;
import com.walkmate.presentation.mapper.user.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller-slice tests for {@code POST /api/v1/auth/google}.
 *
 * Covers the HTTP layer: request parsing, response shape, validation, and
 * error mapping — without touching Firebase or the database.
 * Business-logic scenarios (new user, A2 merge, idempotency) are delegated
 * to {@link UserCommandServiceGoogleTest}.
 */
@WebMvcTest(controllers = UserController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = "app.jwt.secret=test-secret-key-for-unit-testing-at-least-32c")
class GoogleAuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserCommandService userCommandService;

    @MockitoBean
    private UserMapper userMapper;

    // ── Happy path ────────────────────────────────────────────────────────────

    @Test
    void googleLogin_validToken_returns200WithAccessToken() throws Exception {
        LoginResult loginResult = new LoginResult("access-token-xyz", 3600L);
        LoginUserResponse loginResponse = new LoginUserResponse("access-token-xyz", "Bearer", 3600L);

        when(userCommandService.loginOrRegisterWithGoogle(any())).thenReturn(loginResult);
        when(userMapper.toLoginUserResponse(loginResult)).thenReturn(loginResponse);

        mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"firebase-id-token-xyz\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("access-token-xyz"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"));
    }

    /**
     * Simulates the A2 merge and idempotent re-login scenarios.
     * From the HTTP layer's perspective, both resolve to the same 200 response —
     * the service abstracts the distinction.
     */
    @Test
    void googleLogin_existingUserMergeOrIdempotent_returns200() throws Exception {
        LoginResult loginResult = new LoginResult("merged-access-token", 3600L);
        LoginUserResponse loginResponse = new LoginUserResponse("merged-access-token", "Bearer", 3600L);

        when(userCommandService.loginOrRegisterWithGoogle(any())).thenReturn(loginResult);
        when(userMapper.toLoginUserResponse(loginResult)).thenReturn(loginResponse);

        mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"firebase-id-token-existing\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("merged-access-token"));
    }

    // ── Error paths ───────────────────────────────────────────────────────────

    @Test
    void googleLogin_invalidOrExpiredToken_returns400() throws Exception {
        when(userCommandService.loginOrRegisterWithGoogle(any()))
                .thenThrow(new DomainException(UserErrorCode.USER_INVALID_CREDENTIALS));

        mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"expired-or-invalid-token\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("USER_INVALID_CREDENTIALS"));
    }

    @Test
    void googleLogin_providerConflict_returns400() throws Exception {
        when(userCommandService.loginOrRegisterWithGoogle(any()))
                .thenThrow(new DomainException(UserErrorCode.USER_PROVIDER_CONFLICT));

        mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"conflicting-sub-token\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("USER_PROVIDER_CONFLICT"));
    }

    // ── Validation ────────────────────────────────────────────────────────────

    @Test
    void googleLogin_blankIdToken_returns422() throws Exception {
        mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void googleLogin_missingIdTokenField_returns422() throws Exception {
        mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }
}
