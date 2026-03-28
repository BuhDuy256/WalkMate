package com.walkmate.presentation.controller.walkintent;

import com.walkmate.application.user.UserPrincipal;
import com.walkmate.application.walkintent.CreateWalkIntentCommand;
import com.walkmate.application.walkintent.MatchResult;
import com.walkmate.application.walkintent.WalkIntentCommandService;
import com.walkmate.application.walkintent.WalkIntentQueryService;
import com.walkmate.presentation.dto.request.walkintent.CreateWalkIntentRequest;
import com.walkmate.presentation.dto.response.ApiResponse;
import com.walkmate.presentation.dto.response.walkintent.WalkIntentResponse;
import com.walkmate.presentation.mapper.walkintent.WalkIntentMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/intents")
@RequiredArgsConstructor
public class WalkIntentController {

    private final WalkIntentCommandService walkIntentCommandService;
    private final WalkIntentQueryService walkIntentQueryService;
    private final WalkIntentMapper walkIntentMapper;

    /**
     * POST /api/v1/intents
     * Creates a new walk intent for the authenticated user at a given hotspot.
     * userId is resolved from the JWT — never trusted from the request body.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<WalkIntentResponse>> createIntent(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateWalkIntentRequest request) {
        var intent = walkIntentCommandService.createIntent(
            new CreateWalkIntentCommand(
                request.hotspotId(),
                principal.userId(),
                request.timeWindowStart(),
                request.timeWindowEnd(),
                request.ageMin(),
                request.ageMax()
            )
        );
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(walkIntentMapper.toResponse(intent)));
    }

    /**
     * GET /api/v1/intents/{intentId}/match
     * Polls or retrieves a matched intent for the given intentId.
     */
    @GetMapping("/{intentId}/match")
    public ResponseEntity<ApiResponse<WalkIntentResponse>> findMatch(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String intentId) {
        MatchResult result = walkIntentQueryService.findMatch(intentId)
                .orElse(null);
        if (result == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(ApiResponse.success(walkIntentMapper.toResponse(result.matched())));
    }

    /**
     * DELETE /api/v1/intents/{intentId}
     * Cancels an existing walk intent. Only the owner can cancel their own intent.
     */
    @DeleteMapping("/{intentId}")
    public ResponseEntity<ApiResponse<Void>> cancelIntent(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String intentId) {
        walkIntentCommandService.cancelIntent(intentId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
