package com.walkmate.controller;

import com.walkmate.application.SessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * REST Controller for Lifecycle Phase (WalkSessions).
 * It just extracts JSON + JWT data and passes down to Application Service.
 */
@RestController
@RequestMapping("/api/v1/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final SessionService sessionService;

    @PostMapping("/{id}/activate")
    public ResponseEntity<Map<String, String>> activate(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        UUID userId = extractUserId(jwt);
        sessionService.activateSession(id, userId);
        return ResponseEntity.ok(Map.of("message", "Session activated successfully"));
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<Map<String, String>> complete(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        UUID userId = extractUserId(jwt);
        sessionService.completeSession(id, userId);
        return ResponseEntity.ok(Map.of("message", "Session completed successfully"));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<Map<String, String>> cancel(@PathVariable UUID id, 
                                                      @RequestBody Map<String, String> payload, 
                                                      @AuthenticationPrincipal Jwt jwt) {
        UUID userId = extractUserId(jwt);
        String reason = payload.get("reason");
        sessionService.cancelSession(id, userId, reason);
        return ResponseEntity.ok(Map.of("message", "Session cancelled"));
    }

    @PostMapping("/{id}/no-show")
    public ResponseEntity<Map<String, String>> reportNoShow(@PathVariable UUID id, 
                                                            @RequestBody Map<String, String> payload, 
                                                            @AuthenticationPrincipal Jwt jwt) {
        UUID reportingUser = extractUserId(jwt);
        UUID absentUser = UUID.fromString(payload.get("absentUserId"));
        sessionService.reportNoShow(id, reportingUser, absentUser);
        return ResponseEntity.ok(Map.of("message", "No-show reported successfully"));
    }

    private UUID extractUserId(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) {
            // Because security is configured, realistically this is never called, but safe handling:
            return UUID.randomUUID(); 
        }
        return UUID.fromString(jwt.getSubject());
    }
}
