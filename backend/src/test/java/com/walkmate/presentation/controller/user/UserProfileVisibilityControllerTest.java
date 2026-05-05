package com.walkmate.presentation.controller.user;

import com.walkmate.application.user.UserCommandService;
import com.walkmate.application.user.UserProfileCommandService;
import com.walkmate.application.user.UserQueryService;
import com.walkmate.application.user.UserPrincipal;
import com.walkmate.application.social.SocialQueryService;
import com.walkmate.domain.shared.exception.DomainException;
import com.walkmate.domain.user.UserErrorCode;
import com.walkmate.domain.user.VisibilityMode;
import com.walkmate.infrastructure.storage.AvatarStorageService;
import com.walkmate.presentation.exception.GlobalExceptionHandler;
import com.walkmate.presentation.mapper.user.UserProfileMapper;
import com.walkmate.infrastructure.config.SecurityConfig;
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
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = UserProfileController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = "app.jwt.secret=test-secret-key-for-unit-testing-at-least-32c")
class UserProfileVisibilityControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserQueryService userQueryService;

    @MockitoBean
    private SocialQueryService socialQueryService;

    @MockitoBean
    private UserCommandService userCommandService;

    @MockitoBean
    private UserProfileCommandService commandService;

    @MockitoBean
    private AvatarStorageService storageService;

    @MockitoBean
    private UserProfileMapper mapper;

    @Test
    void setVisibility_validRequest_returns200WithMode() throws Exception {
        String userId = UUID.randomUUID().toString();
        when(userCommandService.setVisibilityMode(any())).thenReturn(VisibilityMode.PRIVATE);

        UserPrincipal principal = new UserPrincipal(userId, "me@example.com", "USER");

        mockMvc.perform(patch("/api/v1/users/me/visibility")
                        .with(authentication(new UsernamePasswordAuthenticationToken(principal, null)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"PRIVATE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.visibilityMode").value("PRIVATE"));
    }

    @Test
    void setVisibility_alreadyPrivate_returns400WithErrorCode() throws Exception {
        String userId = UUID.randomUUID().toString();
        when(userCommandService.setVisibilityMode(any()))
                .thenThrow(new DomainException(UserErrorCode.USER_ALREADY_PRIVATE));

        UserPrincipal principal = new UserPrincipal(userId, "me@example.com", "USER");

        mockMvc.perform(patch("/api/v1/users/me/visibility")
                        .with(authentication(new UsernamePasswordAuthenticationToken(principal, null)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"PRIVATE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("USER_ALREADY_PRIVATE"));
    }

    @Test
    void setVisibility_missingMode_returns422() throws Exception {
        String userId = UUID.randomUUID().toString();
        UserPrincipal principal = new UserPrincipal(userId, "me@example.com", "USER");

        mockMvc.perform(patch("/api/v1/users/me/visibility")
                        .with(authentication(new UsernamePasswordAuthenticationToken(principal, null)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }
}
