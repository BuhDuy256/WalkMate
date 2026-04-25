package com.walkmate.data.mapper;

import com.walkmate.data.datasource.remote.dto.response.user.UserProfileResponse;
import com.walkmate.domain.user.UserProfile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class UserProfileMapper {

    private UserProfileMapper() {}

    public static UserProfile toDomain(UserProfileResponse dto) {
        List<String> tags = dto.tags != null
                ? Collections.unmodifiableList(new ArrayList<>(dto.tags))
                : Collections.emptyList();

        return new UserProfile(
                dto.userId,
                dto.fullName,
                dto.gender,
                dto.dateOfBirth,
                dto.avatarUrl,
                dto.bio,
                dto.trustScore,
                dto.totalDistanceKm,
                dto.totalSessions,
                tags
        );
    }
}
