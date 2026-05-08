package com.walkmate.presentation.controller.profile;

import com.walkmate.application.user.UserPrincipal;
import com.walkmate.application.walkpost.WalkPostQueryService;
import com.walkmate.domain.walkpost.WalkPost;
import com.walkmate.presentation.dto.response.ApiResponse;
import com.walkmate.presentation.dto.response.walkpost.WalkPostResponse;
import com.walkmate.presentation.mapper.walkpost.WalkPostMapper;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Profile Posts", description = "Walk post retrieval by profile")
@RestController
@RequiredArgsConstructor
public class ProfilePostController {

    private final WalkPostQueryService walkPostQueryService;

    @GetMapping("/api/v1/profiles/me/posts")
    public ResponseEntity<ApiResponse<List<WalkPostResponse>>> getMyPosts(
            @AuthenticationPrincipal UserPrincipal principal) {

        List<WalkPost> posts = walkPostQueryService.getMyPosts(principal.userId());
        List<WalkPostResponse> responses = posts.stream()
                .map(WalkPostMapper::toResponse)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    @GetMapping("/api/v1/profiles/{userId}/posts")
    public ResponseEntity<ApiResponse<List<WalkPostResponse>>> getUserPosts(
            @AuthenticationPrincipal(errorOnInvalidType = false) UserPrincipal principal,
            @PathVariable String userId) {

        String viewerId = principal != null ? principal.userId() : null;
        List<WalkPost> posts = walkPostQueryService.getUserPosts(userId, viewerId);
        List<WalkPostResponse> responses = posts.stream()
                .map(WalkPostMapper::toResponse)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(responses));
    }
}
