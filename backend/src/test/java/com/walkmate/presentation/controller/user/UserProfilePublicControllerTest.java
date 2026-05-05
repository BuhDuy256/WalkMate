package com.walkmate.presentation.controller.user;

import com.walkmate.application.social.FriendshipStatusResult;
import com.walkmate.application.social.SocialQueryService;
import com.walkmate.application.user.UserQueryService;
import com.walkmate.infrastructure.config.SecurityConfig;
import com.walkmate.presentation.dto.response.user.UserProfileResponse;
import com.walkmate.presentation.exception.GlobalExceptionHandler;
import com.walkmate.presentation.mapper.user.UserProfileMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = UserProfileController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = "app.jwt.secret=test-secret-key-for-unit-testing-at-least-32c")
class UserProfilePublicControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserQueryService userQueryService;

    @MockitoBean
    private SocialQueryService socialQueryService;

    @MockitoBean
    private UserProfileMapper mapper;

    // ── Unauthenticated public view ──────────────────────────────────────────

    @Test
    void getPublicProfile_unauthenticated_returnsProfileWithoutFriendFields() throws Exception {
        String userId = UUID.randomUUID().toString();

        when(userQueryService.getProfile(any())).thenReturn(null);
        when(userQueryService.getUser(any())).thenReturn(null);
        when(userQueryService.getTagsByUserId(any())).thenReturn(List.of());

        UserProfileResponse resp = new UserProfileResponse(
                userId, "Alice", null, null, null, "bio", 10, 1.2, 3, List.of(), null, null, null);

        when(mapper.toResponse(any(), any(), any(), any(), any(), any())).thenReturn(resp);

        mockMvc.perform(get("/api/v1/users/" + userId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").value(userId))
                .andExpect(jsonPath("$.data.lastActiveAt").isEmpty())
                .andExpect(jsonPath("$.data.friendshipStatus").isEmpty());
    }

    // ── Authenticated friend view ──────────────────────────────────────────

    @Test
    void getPublicProfile_authenticatedFriend_includesLastActiveAndFriendsStatus() throws Exception {
        String userId = UUID.randomUUID().toString();
        String callerId = UUID.randomUUID().toString();

        when(userQueryService.getProfile(any())).thenReturn(null);
        when(userQueryService.getUser(any())).thenReturn(null);
        when(userQueryService.getTagsByUserId(any())).thenReturn(List.of());
        when(socialQueryService.getFriendshipStatus(any(), any()))
                .thenReturn(new FriendshipStatusResult("FRIENDS", null));
        when(userQueryService.getLastActiveAt(any())).thenReturn(Instant.parse("2026-05-05T12:00:00Z"));

        UserProfileResponse resp = new UserProfileResponse(
                userId, "Bob", null, null, null, "bio2", 20, 2.3, 5, List.of(), "2026-05-05T12:00:00Z", "FRIENDS", null);

        when(mapper.toResponse(any(), any(), any(), any(), any(), any())).thenReturn(resp);

        mockMvc.perform(get("/api/v1/users/" + userId)
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                new com.walkmate.application.user.UserPrincipal(callerId, "caller@example.com", "USER"), null)))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").value(userId))
                .andExpect(jsonPath("$.data.lastActiveAt").value("2026-05-05T12:00:00Z"))
                .andExpect(jsonPath("$.data.friendshipStatus").value("FRIENDS"));
    }

    // ── Authenticated pending-received view ─────────────────────────────────

    @Test
    void getPublicProfile_authenticatedPendingReceived_includesPendingRequestId() throws Exception {
        String userId = UUID.randomUUID().toString();
        String callerId = UUID.randomUUID().toString();

        when(userQueryService.getProfile(any())).thenReturn(null);
        when(userQueryService.getUser(any())).thenReturn(null);
        when(userQueryService.getTagsByUserId(any())).thenReturn(List.of());
        when(socialQueryService.getFriendshipStatus(any(), any()))
                .thenReturn(new FriendshipStatusResult("PENDING_RECEIVED", "req-123"));

        UserProfileResponse resp = new UserProfileResponse(
                userId, "Carol", null, null, null, "bio3", 30, 3.4, 7, List.of(), null, "PENDING_RECEIVED", "req-123");

        when(mapper.toResponse(any(), any(), any(), any(), any(), any())).thenReturn(resp);

        mockMvc.perform(get("/api/v1/users/" + userId)
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                new com.walkmate.application.user.UserPrincipal(callerId, "caller@example.com", "USER"), null)))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.friendshipStatus").value("PENDING_RECEIVED"))
                .andExpect(jsonPath("$.data.pendingRequestId").value("req-123"));
    }
}
