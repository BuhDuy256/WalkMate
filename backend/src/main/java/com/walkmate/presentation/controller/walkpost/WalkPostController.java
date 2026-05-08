package com.walkmate.presentation.controller.walkpost;

import com.walkmate.application.user.UserPrincipal;
import com.walkmate.application.walkpost.CreateWalkPostCommand;
import com.walkmate.application.walkpost.UpdateWalkPostVisibilityCommand;
import com.walkmate.application.walkpost.WalkPostCommandService;
import com.walkmate.domain.walkpost.WalkPost;
import com.walkmate.presentation.dto.request.walkpost.CreateWalkPostRequest;
import com.walkmate.presentation.dto.request.walkpost.UpdateWalkPostVisibilityRequest;
import com.walkmate.presentation.dto.response.ApiResponse;
import com.walkmate.presentation.dto.response.walkpost.WalkPostResponse;
import com.walkmate.presentation.mapper.walkpost.WalkPostMapper;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Walk Posts", description = "Walk result post management")
@RestController
@RequiredArgsConstructor
public class WalkPostController {

    private final WalkPostCommandService walkPostCommandService;

    @PostMapping("/api/v1/sessions/{sessionId}/posts")
    public ResponseEntity<ApiResponse<WalkPostResponse>> createPost(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String sessionId,
            @Valid @RequestBody CreateWalkPostRequest request) {

        WalkPost post = walkPostCommandService.createPost(new CreateWalkPostCommand(
                sessionId,
                principal.userId(),
                request.caption(),
                request.visibility(),
                request.showCompanion(),
                request.showRouteMap(),
                request.showStats()
        ));
        return ResponseEntity.ok(ApiResponse.success(WalkPostMapper.toResponse(post)));
    }

    @PatchMapping("/api/v1/walk-posts/{postId}/visibility")
    public ResponseEntity<ApiResponse<WalkPostResponse>> updateVisibility(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String postId,
            @Valid @RequestBody UpdateWalkPostVisibilityRequest request) {

        WalkPost post = walkPostCommandService.updateVisibility(new UpdateWalkPostVisibilityCommand(
                postId,
                principal.userId(),
                request.visibility()
        ));
        return ResponseEntity.ok(ApiResponse.success(WalkPostMapper.toResponse(post)));
    }

    @DeleteMapping("/api/v1/walk-posts/{postId}")
    public ResponseEntity<ApiResponse<Void>> deletePost(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String postId) {

        walkPostCommandService.deletePost(postId, principal.userId());
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
