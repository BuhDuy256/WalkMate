package com.walkmate.presentation.controller.chat;

import com.walkmate.application.chat.ChatQueryService;
import com.walkmate.application.user.UserPrincipal;
import com.walkmate.domain.chat.ChatMessage;
import com.walkmate.presentation.dto.response.ApiResponse;
import com.walkmate.presentation.dto.response.chat.ChatMessageResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST endpoint for chat history.
 *
 * Authentication: covered by SecurityConfig — /api/v1/sessions/** requires a valid JWT.
 * Authorization:  ChatQueryService verifies the caller is a participant before returning data.
 * Exceptions:     DomainException bubbles to GlobalExceptionHandler — no try-catch here.
 */
@Tag(name = "Chat", description = "Session-scoped chat history")
@RestController
@RequestMapping("/api/v1/sessions")
@RequiredArgsConstructor
public class ChatController {

    private final ChatQueryService chatQueryService;

    /**
     * GET /api/v1/sessions/{sessionId}/chat/messages?limit=50
     *
     * Returns the most recent messages for the session, ordered oldest-first.
     * Max limit is capped at 100 inside ChatQueryService regardless of the
     * value supplied here.
     */
    @GetMapping("/{sessionId}/chat/messages")
    public ResponseEntity<ApiResponse<List<ChatMessageResponse>>> getChatHistory(
            @PathVariable String sessionId,
            @RequestParam(defaultValue = "50") int limit,
            @AuthenticationPrincipal UserPrincipal principal) {

        List<ChatMessage> messages =
                chatQueryService.findRecentMessages(sessionId, principal.userId(), limit);

        List<ChatMessageResponse> response = messages.stream()
                .map(ChatMessageResponse::from)
                .toList();

        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
