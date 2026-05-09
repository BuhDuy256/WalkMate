package com.walkmate.data.mapper;

import com.walkmate.data.datasource.remote.dto.response.walkpost.WalkPostResponse;
import com.walkmate.domain.walkpost.PostVisibility;
import com.walkmate.domain.walkpost.WalkPost;

import java.util.ArrayList;
import java.util.List;

public class WalkPostMapper {

    public static WalkPost toDomain(WalkPostResponse dto) {
        return new WalkPost(
                dto.getPostId(),
                dto.getSessionId(),
                dto.getAuthorId(),
                dto.getAuthorName(),
                dto.getAuthorAvatarUrl(),
                dto.getCaption(),
                PostVisibility.from(dto.getVisibility()),
                dto.getHotspotName(),
                dto.getDistanceKm(),
                dto.getDurationSeconds(),
                dto.getPointsEarned(),
                dto.isShowCompanion(),
                dto.isShowRouteMap(),
                dto.isShowStats(),
                dto.getCompanionName(),
                dto.getRoutePreviewUrl(),
                dto.getRoutePreviewStatus(),
                dto.getCreatedAt()
        );
    }

    public static List<WalkPost> toDomainList(List<WalkPostResponse> dtos) {
        List<WalkPost> result = new ArrayList<>(dtos.size());
        for (WalkPostResponse dto : dtos) {
            result.add(toDomain(dto));
        }
        return result;
    }

    private WalkPostMapper() {}
}
