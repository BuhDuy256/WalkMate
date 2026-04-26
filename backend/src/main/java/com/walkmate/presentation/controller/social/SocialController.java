package com.walkmate.presentation.controller.social;

import com.walkmate.application.social.SocialCommandService;
import com.walkmate.application.social.SocialQueryService;
import com.walkmate.application.user.UserPrincipal;
import com.walkmate.application.user.UserQueryService;
import com.walkmate.domain.user.User;
import com.walkmate.domain.user.UserProfile;
import com.walkmate.presentation.dto.response.ApiResponse;
import com.walkmate.presentation.dto.response.social.UserSummaryResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Social", description = "Follow and block management endpoints")
@RestController
@RequiredArgsConstructor
public class SocialController {

    private final SocialCommandService commandService;
    private final SocialQueryService   queryService;
    private final UserQueryService     userQueryService;

    /**
     * GET /api/v1/users/me/friends
     *
     * Returns the caller's accepted friends for the private-intent friend-picker (UC-08).
     * Backed by FriendQueryService which reads accepted friendship rows.
     */
    @GetMapping("/api/v1/users/me/friends")
    public ResponseEntity<ApiResponse<List<UserSummaryResponse>>> getFriends(
            @AuthenticationPrincipal UserPrincipal principal) {

        UUID callerId = UUID.fromString(principal.userId());
        List<UserSummaryResponse> result = queryService.getFriends(callerId)
                .stream()
                .map(this::toSummary)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // ── Block ─────────────────────────────────────────────────────────────────

    /** POST /api/v1/users/{userId}/block */
    @PostMapping("/api/v1/users/{userId}/block")
    public ResponseEntity<ApiResponse<Void>> block(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String userId) {

        UUID callerId = UUID.fromString(principal.userId());
        UUID targetId = UUID.fromString(userId);
        commandService.block(callerId, targetId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /** DELETE /api/v1/users/{userId}/block */
    @DeleteMapping("/api/v1/users/{userId}/block")
    public ResponseEntity<ApiResponse<Void>> unblock(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String userId) {

        UUID callerId = UUID.fromString(principal.userId());
        UUID targetId = UUID.fromString(userId);
        commandService.unblock(callerId, targetId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private UserSummaryResponse toSummary(UUID userId) {
        UserProfile profile = userQueryService.getProfile(userId);
        User        user    = userQueryService.getUser(userId);
        return new UserSummaryResponse(
                userId.toString(),
                profile.getFullName(),
                profile.getAvatarUrl(),
                user.getTrustScore()
        );
    }
}
