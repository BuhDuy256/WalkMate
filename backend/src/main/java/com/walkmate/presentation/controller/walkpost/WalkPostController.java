package com.walkmate.presentation.controller.walkpost;

import com.walkmate.application.user.UserPrincipal;
import com.walkmate.application.walkpost.CreateWalkPostCommand;
import com.walkmate.application.walkpost.UpdateWalkPostVisibilityCommand;
import com.walkmate.application.walkpost.WalkPostCommandService;
import com.walkmate.application.walkpost.WalkPostQueryService;
import com.walkmate.application.walkpost.WalkPostRoutePreviewService;
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

    private final WalkPostCommandService      walkPostCommandService;
    private final WalkPostQueryService        walkPostQueryService;
    private final WalkPostRoutePreviewService walkPostRoutePreviewService;

    /**
     * Creates a walk post, then triggers server-side route preview generation.
     *
     * The post-creation transaction commits before preview generation begins,
     * so the preview UPDATE does not contend with the INSERT row lock.
     * The response includes the final route_preview_url and route_preview_status
     * (READY, NO_ROUTE, or FAILED) after the generation attempt completes.
     */
    @PostMapping("/api/v1/sessions/{sessionId}/posts")
    public ResponseEntity<ApiResponse<WalkPostResponse>> createPost(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String sessionId,
            @Valid @RequestBody CreateWalkPostRequest request) {

        // TX1: validate + INSERT walk_post (status=PENDING). Transaction commits here.
        WalkPost saved = walkPostCommandService.createPost(new CreateWalkPostCommand(
                sessionId,
                principal.userId(),
                request.caption(),
                request.visibility(),
                request.showCompanion(),
                request.showRouteMap(),
                request.showStats()
        ));

        // After TX1 commits: generate static map → upload to Supabase → UPDATE route preview fields.
        // Failures are caught internally; post creation is not rolled back.
        walkPostRoutePreviewService.generateAndPersist(
                saved.getPostId(), sessionId, principal.userId());

        // Re-fetch to include the updated routePreviewUrl / routePreviewStatus in the response.
        WalkPost finalPost = walkPostQueryService.getPostById(saved.getPostId());
        return ResponseEntity.ok(ApiResponse.success(WalkPostMapper.toResponse(finalPost)));
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

    /**
     * Deletes the walk post row, then attempts to remove the stored preview image from Supabase.
     * The storage delete is best-effort — failures are logged but do not affect the HTTP response.
     */
    @DeleteMapping("/api/v1/walk-posts/{postId}")
    public ResponseEntity<ApiResponse<Void>> deletePost(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String postId) {

        // Load path BEFORE deletion (row disappears after delete).
        String storagePath = walkPostQueryService.getRoutePreviewPath(postId);

        walkPostCommandService.deletePost(postId, principal.userId());

        walkPostRoutePreviewService.deleteStorageImageQuietly(storagePath);

        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
