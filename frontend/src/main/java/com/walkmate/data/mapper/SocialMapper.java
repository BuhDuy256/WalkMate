package com.walkmate.data.mapper;

import com.walkmate.data.datasource.remote.dto.response.social.FriendRequestResponse;
import com.walkmate.data.datasource.remote.dto.response.social.PublicUserResponse;
import com.walkmate.data.datasource.remote.dto.response.social.UserSummaryResponse;
import com.walkmate.domain.social.FriendRequest;
import com.walkmate.domain.social.UserSummary;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public final class SocialMapper {

    private SocialMapper() {}

    public static UserSummary toDomain(UserSummaryResponse dto) {
        if (dto == null) return null;
        return new UserSummary(dto.userId, dto.fullName, dto.avatarUrl, dto.trustScore,
                "NONE", null, null, null, null);
    }

    public static List<UserSummary> toDomainList(List<UserSummaryResponse> dtos) {
        if (dtos == null) return Collections.emptyList();
        return dtos.stream()
                .map(SocialMapper::toDomain)
                .collect(Collectors.toList());
    }

    public static UserSummary toUserSummary(PublicUserResponse dto) {
        if (dto == null) return null;
        return new UserSummary(dto.userId, dto.fullName, dto.avatarUrl, (int) dto.trustScore,
                dto.friendshipStatus, dto.bio, dto.tags, dto.pendingRequestId, dto.lastActiveAt);
    }

    public static FriendRequest toFriendRequest(FriendRequestResponse dto) {
        if (dto == null) return null;
        return new FriendRequest(
                dto.requestId,
                dto.senderId,
                dto.senderName,
                dto.senderAvatarUrl,
                dto.receiverId,
                dto.status,
                dto.createdAt
        );
    }

    public static List<FriendRequest> toFriendRequestList(List<FriendRequestResponse> dtos) {
        if (dtos == null) return Collections.emptyList();
        return dtos.stream()
                .map(SocialMapper::toFriendRequest)
                .collect(Collectors.toList());
    }
}
