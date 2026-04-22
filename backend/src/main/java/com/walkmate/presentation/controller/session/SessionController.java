package com.walkmate.presentation.controller.session;

import com.walkmate.application.session.SessionCommandService;
import com.walkmate.application.user.UserPrincipal;
import com.walkmate.application.user.UserQueryService;
import com.walkmate.domain.session.WalkSession;
import com.walkmate.presentation.dto.request.session.CancelWalkSessionRequest;
import com.walkmate.presentation.dto.response.ApiResponse;
import com.walkmate.presentation.dto.response.session.WalkSessionResponse;
import com.walkmate.presentation.mapper.session.SessionMapper;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Sessions", description = "Walk session lifecycle management")
@RestController
@RequestMapping("/api/v1/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final SessionCommandService sessionCommandService;
    private final SessionMapper         sessionMapper;
    private final UserQueryService      userQueryService;

    /**
     * GET /api/v1/sessions/active
     * Returns all PENDING and ACTIVE sessions for the authenticated user.
     */
    @GetMapping("/active")
    public ResponseEntity<ApiResponse<List<WalkSessionResponse>>> getActiveSessions(
            @AuthenticationPrincipal UserPrincipal principal) {

        List<WalkSessionResponse> responses = sessionCommandService
                .getActiveSessions(principal.userId())
                .stream()
                .map(s -> {
                    boolean callerIsA = principal.userId().equals(s.getUserIdA());
                    String partnerId = callerIsA ? s.getUserIdB() : s.getUserIdA();
                    return sessionMapper.toResponse(s, false, resolveDisplayName(partnerId));
                })
                .toList();

        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    /**
     * POST /api/v1/sessions/{sessionId}/activate
     * Records the caller's arrival at the meeting point.
     * Returns the updated session (PENDING until both activate, then ACTIVE).
     */
    @PostMapping("/{sessionId}/activate")
    public ResponseEntity<ApiResponse<WalkSessionResponse>> activateSession(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String sessionId) {

        WalkSession session = sessionCommandService.activateSession(sessionId, principal.userId());
        boolean callerIsA = principal.userId().equals(session.getUserIdA());
        String partnerId = callerIsA ? session.getUserIdB() : session.getUserIdA();
        return ResponseEntity.ok(ApiResponse.success(
                sessionMapper.toResponse(session, false, resolveDisplayName(partnerId))));
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private String resolveDisplayName(String userId) {
        try {
            return userQueryService.getDisplayName(UUID.fromString(userId));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * POST /api/v1/sessions/{sessionId}/cancel
     * Cancels a PENDING session. Either participant may cancel.
     */
    @PostMapping("/{sessionId}/cancel")
    public ResponseEntity<ApiResponse<Void>> cancelSession(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String sessionId,
            @Valid @RequestBody CancelWalkSessionRequest request) {

        sessionCommandService.cancelSession(sessionId, principal.userId(), request.reason());
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /**
     * POST /api/v1/sessions/{sessionId}/complete
     * User-initiated session completion. Enforces S-5 (minimum 5-minute guard).
     */
    @PostMapping("/{sessionId}/complete")
    public ResponseEntity<ApiResponse<WalkSessionResponse>> completeSession(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String sessionId) {
        WalkSession session = sessionCommandService.completeSession(sessionId, principal.userId());
        boolean callerIsA = principal.userId().equals(session.getUserIdA());
        String partnerId = callerIsA ? session.getUserIdB() : session.getUserIdA();
        return ResponseEntity.ok(ApiResponse.success(
                sessionMapper.toResponse(session, false, resolveDisplayName(partnerId))));
    }
}
