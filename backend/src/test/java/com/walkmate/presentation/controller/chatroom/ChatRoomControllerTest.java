package com.walkmate.presentation.controller.chatroom;

import com.walkmate.application.chatroom.ChatRoomCommandService;
import com.walkmate.application.user.UserPrincipal;
import com.walkmate.infrastructure.security.jwt.UserPrincipalConverter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller-slice tests for {@link ChatRoomController}.
 *
 * Scope: HTTP contract only — correct status codes and ApiResponse shape.
 * DomainException → HTTP mapping is NOT tested here (see GlobalExceptionHandlerTest).
 * The CommandService is mocked entirely; its behavior is covered by ChatRoomCommandServiceTest.
 *
 * Per TESTING.md §4.3: exactly 2 scenarios per endpoint (happy path + validation failure).
 * Per TESTING.md §5: no try/catch, no @SpringBootTest.
 */
@WebMvcTest(ChatRoomController.class)
class ChatRoomControllerTest {

    private static final String ROOM_ID = "room-abc-123";
    private static final UsernamePasswordAuthenticationToken AUTH =
            new UsernamePasswordAuthenticationToken(
                    new UserPrincipal("user-abc", "user@test.com"), null, List.of());

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ChatRoomCommandService chatRoomCommandService;

    // @WebMvcTest does NOT load infrastructure beans (SecurityConfig included).
    // Default Spring Boot test security is active (CSRF enabled, session-based).
    // .with(csrf()) is required on every POST to satisfy the default CSRF filter.
    // UserPrincipalConverter mock is present as a safety net in case the production
    // SecurityConfig is ever explicitly imported into this slice.
    @MockitoBean
    UserPrincipalConverter userPrincipalConverter;

    // =========================================================================
    // POST /api/v1/chat/rooms/{roomId}/messages — sendMessage
    // =========================================================================

    @Test
    void sendMessage_shouldReturn201_whenRequestIsValid() throws Exception {
        mockMvc.perform(post("/api/v1/chat/rooms/{roomId}/messages", ROOM_ID)
                        .with(authentication(AUTH))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content": "Hello there!"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void sendMessage_shouldReturn422_whenContentIsMissing() throws Exception {
        mockMvc.perform(post("/api/v1/chat/rooms/{roomId}/messages", ROOM_ID)
                        .with(authentication(AUTH))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false));
    }

    // =========================================================================
    // POST /api/v1/chat/rooms/{roomId}/read — markMessagesAsRead
    // =========================================================================

    @Test
    void markMessagesAsRead_shouldReturn200_whenRequestIsValid() throws Exception {
        mockMvc.perform(post("/api/v1/chat/rooms/{roomId}/read", ROOM_ID)
                        .with(authentication(AUTH))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"last_read_message_id": "msg-789"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void markMessagesAsRead_shouldReturn422_whenLastReadMessageIdIsMissing() throws Exception {
        mockMvc.perform(post("/api/v1/chat/rooms/{roomId}/read", ROOM_ID)
                        .with(authentication(AUTH))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false));
    }
}
