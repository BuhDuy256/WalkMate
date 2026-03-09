package com.walkmate.controller;

import com.walkmate.application.IntentService;
import com.walkmate.controller.dto.request.CreateIntentRequest;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Controller mapping UI form to IntentService operations.
 */
@RestController
@RequestMapping("/api/v1/intents")
@RequiredArgsConstructor
public class IntentController {

    private final IntentService intentService;

    @PostMapping
    public ResponseEntity<Map<String, Object>> createIntent(
            @RequestBody CreateIntentRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        
        UUID userId = extractUserId(jwt);

        // Assume intent expires at the end of the time window
        LocalDateTime expiresAt = request.getTimeWindowEnd();

        UUID intentId = intentService.createWalkIntent(
                userId,
                request.getLocationLat(),
                request.getLocationLng(),
                request.getTimeWindowStart(),
                request.getTimeWindowEnd(),
                request.getWalkType(),
                request.getRadiusMeters(),
                request.getExtraPreferences(),
                expiresAt
        );

        // Success response with generated ID
        return ResponseEntity.ok(Map.of(
                "message", "Walk Intent created successfully. We'll match you with nearby buddies!",
                "intentId", intentId
        ));
    }

    private UUID extractUserId(Jwt jwt) {
        // Fallback or explicit check for testing cases. 
        if (jwt == null || jwt.getSubject() == null) {
            return UUID.randomUUID(); 
        }
        return UUID.fromString(jwt.getSubject());
    }
}
