package com.walkmate.presentation.mapper.walkpost;

import com.walkmate.domain.walkpost.WalkPost;
import com.walkmate.presentation.dto.response.walkpost.WalkPostResponse;

public class WalkPostMapper {

    public static WalkPostResponse toResponse(WalkPost post) {
        return new WalkPostResponse(
                post.getPostId(),
                post.getSessionId(),
                post.getAuthorId(),
                post.getAuthorName(),
                post.getAuthorAvatarUrl(),
                post.getCaption(),
                post.getVisibility().name(),
                post.getHotspotName(),
                post.getDistanceKm(),
                post.getDurationSeconds(),
                post.getPointsEarned(),
                post.isShowCompanion(),
                post.isShowRouteMap(),
                post.isShowStats(),
                post.getCompanionName(),
                post.getRoutePreviewUrl(),
                post.getCreatedAt() != null ? post.getCreatedAt().toString() : null
        );
    }

    private WalkPostMapper() {}
}
