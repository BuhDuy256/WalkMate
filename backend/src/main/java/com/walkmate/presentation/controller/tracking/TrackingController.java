package com.walkmate.presentation.controller.tracking;

import com.walkmate.application.tracking.TrackingCommandService;
import com.walkmate.application.user.UserPrincipal;
import com.walkmate.presentation.dto.request.tracking.PushRoutePointsRequest;
import com.walkmate.presentation.dto.response.ApiResponse;
import com.walkmate.presentation.dto.response.tracking.PushRoutePointsResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Tracking", description = "GPS route-point sync for active walk sessions")
@RestController
@RequestMapping("/api/v1/tracking")
@RequiredArgsConstructor
public class TrackingController {

    private final TrackingCommandService trackingCommandService;

    /**
     * POST /api/v1/tracking/sync
     *
     * Accepts a batch of GPS route points for an ACTIVE session and persists them
     * as a compressed chunk. Returns the echoed {@code localId} values so the Android
     * client can mark exactly those Room rows as synced.
     *
     * <p>Requires a valid JWT. The session must be ACTIVE and the caller must be a participant.</p>
     */
    @PostMapping("/sync")
    public ResponseEntity<ApiResponse<PushRoutePointsResponse>> syncRoutePoints(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody PushRoutePointsRequest request) {

        PushRoutePointsResponse response = trackingCommandService.syncRoutePoints(
                request.syncRequestId(), request.sessionId(), principal.userId(), request.points());

        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
