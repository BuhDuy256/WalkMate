package com.walkmate.presentation.controller.session;

import com.walkmate.application.session.SessionHistoryQueryService;
import com.walkmate.application.tracking.TrackingQueryService;
import com.walkmate.application.user.UserPrincipal;
import com.walkmate.presentation.dto.response.ApiResponse;
import com.walkmate.presentation.dto.response.session.SessionRouteResponse;
import com.walkmate.presentation.dto.response.session.SessionSummaryResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Session History", description = "Session history and GPS route retrieval")
@RestController
@RequestMapping("/api/v1/sessions")
@RequiredArgsConstructor
public class SessionHistoryController {

    private final SessionHistoryQueryService historyQueryService;
    private final TrackingQueryService       trackingQueryService;

    /**
     * GET /api/v1/sessions/history
     * Returns terminal sessions for the authenticated user, newest first.
     */
    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<SessionSummaryResponse>>> getSessionHistory(
            @AuthenticationPrincipal UserPrincipal principal) {

        List<SessionSummaryResponse> history = historyQueryService.getSessionHistory(principal.userId());
        return ResponseEntity.ok(ApiResponse.success(history));
    }

    /**
     * GET /api/v1/sessions/{sessionId}/route
     * Returns dual-path GPS polylines and walk stats for a finished session.
     * Caller must be a participant. PENDING and ACTIVE sessions are rejected.
     */
    @GetMapping("/{sessionId}/route")
    public ResponseEntity<ApiResponse<SessionRouteResponse>> getSessionRoute(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String sessionId) {

        SessionRouteResponse response = trackingQueryService.getSessionRoute(sessionId, principal.userId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
