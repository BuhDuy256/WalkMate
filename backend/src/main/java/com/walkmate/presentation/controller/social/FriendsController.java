package com.walkmate.presentation.controller.social;

import com.walkmate.application.social.FriendCommandService;
import com.walkmate.application.social.FriendQueryService;
import com.walkmate.application.user.UserPrincipal;
import com.walkmate.application.user.UserQueryService;
import com.walkmate.domain.social.Friendship;
import com.walkmate.domain.user.UserProfile;
import com.walkmate.presentation.dto.response.ApiResponse;
import com.walkmate.presentation.dto.response.social.FriendshipResponse;
import com.walkmate.presentation.dto.response.social.UserSummaryResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Friends", description = "Friend request and friendship management (UC-34–38)")
@RestController
@RequestMapping("/api/v1/friends")
@RequiredArgsConstructor
public class FriendsController {

    private final FriendCommandService friendCommandService;
    private final FriendQueryService   friendQueryService;
    private final UserQueryService     userQueryService;

    // ── Send friend request (UC-34) ───────────────────────────────────────────

    @PostMapping("/{userId}/request")
    public ResponseEntity<ApiResponse<FriendshipResponse>> sendFriendRequest(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String userId) {

        UUID callerId = UUID.fromString(principal.userId());
        UUID targetId = UUID.fromString(userId);
        Friendship fs = friendCommandService.sendFriendRequest(callerId, targetId);
        return ResponseEntity.status(201).body(ApiResponse.success(toFriendshipResponse(fs)));
    }

    // ── Accept friend request (UC-35) ─────────────────────────────────────────

    @PostMapping("/requests/{requestId}/accept")
    public ResponseEntity<ApiResponse<FriendshipResponse>> acceptFriendRequest(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String requestId) {

        UUID callerId = UUID.fromString(principal.userId());
        Friendship fs = friendCommandService.acceptFriendRequest(callerId, requestId);
        return ResponseEntity.ok(ApiResponse.success(toFriendshipResponse(fs)));
    }

    // ── Decline friend request (UC-35) ────────────────────────────────────────

    @PostMapping("/requests/{requestId}/decline")
    public ResponseEntity<ApiResponse<Void>> declineFriendRequest(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String requestId) {

        UUID callerId = UUID.fromString(principal.userId());
        friendCommandService.declineFriendRequest(callerId, requestId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // ── Get friends list (UC-37) ──────────────────────────────────────────────

    @GetMapping
    public ResponseEntity<ApiResponse<List<UserSummaryResponse>>> getFriends(
            @AuthenticationPrincipal UserPrincipal principal) {

        UUID callerId = UUID.fromString(principal.userId());
        List<UserSummaryResponse> result = friendQueryService.getFriends(callerId)
                .stream()
                .map(uid -> {
                    UserProfile profile = userQueryService.getProfile(uid);
                    return new UserSummaryResponse(uid.toString(), profile.getFullName(), profile.getAvatarUrl());
                })
                .toList();
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // ── Get incoming requests (UC-38) ─────────────────────────────────────────

    @GetMapping("/requests/incoming")
    public ResponseEntity<ApiResponse<List<FriendshipResponse>>> getIncomingRequests(
            @AuthenticationPrincipal UserPrincipal principal) {

        UUID callerId = UUID.fromString(principal.userId());
        List<FriendshipResponse> result = friendQueryService.getIncomingRequests(callerId)
                .stream()
                .map(this::toFriendshipResponse)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // ── Get outgoing requests (UC-38) ─────────────────────────────────────────

    @GetMapping("/requests/outgoing")
    public ResponseEntity<ApiResponse<List<FriendshipResponse>>> getOutgoingRequests(
            @AuthenticationPrincipal UserPrincipal principal) {

        UUID callerId = UUID.fromString(principal.userId());
        List<FriendshipResponse> result = friendQueryService.getOutgoingRequests(callerId)
                .stream()
                .map(this::toFriendshipResponse)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // ── Remove friend (UC-36) ─────────────────────────────────────────────────

    @DeleteMapping("/{userId}")
    public ResponseEntity<ApiResponse<Void>> removeFriend(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String userId) {

        UUID callerId = UUID.fromString(principal.userId());
        UUID targetId = UUID.fromString(userId);
        friendCommandService.removeFriend(callerId, targetId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private FriendshipResponse toFriendshipResponse(Friendship fs) {
        return new FriendshipResponse(
                fs.getFriendshipId(),
                fs.getRequesterId().toString(),
                fs.getAddresseeId().toString(),
                fs.getStatus(),
                fs.getCreatedAt().toString());
    }
}
