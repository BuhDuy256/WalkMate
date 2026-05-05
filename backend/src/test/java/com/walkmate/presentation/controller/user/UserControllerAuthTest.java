package com.walkmate.presentation.controller.user;

import com.walkmate.application.user.LoginResult;
import com.walkmate.application.user.UserCommandService;
import com.walkmate.application.user.UserPrincipal;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = UserController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = "app.jwt.secret=test-secret-key-for-unit-testing-at-least-32c")
class UserControllerAuthTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserCommandService userCommandService;

    @MockitoBean
    private UserMapper userMapper;

    // ── Register ─────────────────────────────────────────────────────────────

    @Test
    void registerUser_validRequest_returns201() throws Exception {
        LoginResult loginResult = new LoginResult("access-token-1", 3600L, "refresh-token-1", 2592000L);
        LoginUserResponse loginResponse = new LoginUserResponse("access-token-1", "Bearer", 3600L, "refresh-token-1", 2592000L);

        when(userCommandService.registerUser(any())).thenReturn(loginResult);
        when(userMapper.toLoginUserResponse(loginResult)).thenReturn(loginResponse);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullname\":\"Test User\",\"email\":\"test@example.com\",\"password\":\"P@ssw0rd\",\"deviceId\":\"device-test\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("access-token-1"));
    }

    @Test
    void registerUser_missingEmail_returns422() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullname\":\"Test User\",\"password\":\"P@ssw0rd\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    // ── Login ────────────────────────────────────────────────────────────────

    @Test
    void loginUser_valid_returns200() throws Exception {
        LoginResult loginResult = new LoginResult("access-token-2", 3600L, "refresh-token-2", 2592000L);
        LoginUserResponse loginResponse = new LoginUserResponse("access-token-2", "Bearer", 3600L, "refresh-token-2", 2592000L);

        when(userCommandService.loginUser(any())).thenReturn(loginResult);
        when(userMapper.toLoginUserResponse(loginResult)).thenReturn(loginResponse);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"test@example.com\",\"password\":\"P@ssw0rd\",\"deviceId\":\"device-test\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("access-token-2"));
    }

    @Test
    void loginUser_invalidCredentials_returns400() throws Exception {
        when(userCommandService.loginUser(any()))
                .thenThrow(new DomainException(UserErrorCode.USER_INVALID_CREDENTIALS));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"bad@example.com\",\"password\":\"wrong\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("USER_INVALID_CREDENTIALS"));
    }

    // ── Refresh ─────────────────────────────────────────────────────────────

    @Test
    void refreshToken_valid_returns200() throws Exception {
        LoginResult loginResult = new LoginResult("access-token-3", 3600L, "refresh-token-3", 2592000L);
        LoginUserResponse loginResponse = new LoginUserResponse("access-token-3", "Bearer", 3600L, "refresh-token-3", 2592000L);

        when(userCommandService.refreshToken(any())).thenReturn(loginResult);
        when(userMapper.toLoginUserResponse(loginResult)).thenReturn(loginResponse);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"refresh-token-3\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("access-token-3"));
    }

    @Test
    void refreshToken_missingField_returns422() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    // ── Logout ──────────────────────────────────────────────────────────────

    @Test
    void logout_authenticated_callsServiceAndReturns200() throws Exception {
        String userId = UUID.randomUUID().toString();
        UserPrincipal principal = new UserPrincipal(userId, "me@example.com", "USER");

        mockMvc.perform(post("/api/v1/auth/logout")
                        .with(authentication(new UsernamePasswordAuthenticationToken(principal, null)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deviceId\":\"device-test\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(userCommandService).logout(UUID.fromString(userId), "device-test");
    }

    @Test
    void logoutAll_authenticated_callsServiceAndReturns200() throws Exception {
        String userId = UUID.randomUUID().toString();
        UserPrincipal principal = new UserPrincipal(userId, "me@example.com", "USER");

        mockMvc.perform(post("/api/v1/auth/logout-all")
                        .with(authentication(new UsernamePasswordAuthenticationToken(principal, null)))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(userCommandService).logoutAll(UUID.fromString(userId));
    }

}
