package com.walkmate.data.mapper;

import com.walkmate.data.datasource.remote.dto.response.social.UserSummaryResponse;
import com.walkmate.domain.social.UserSummary;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public final class SocialMapper {

    private SocialMapper() {}

    public static UserSummary toDomain(UserSummaryResponse dto) {
        if (dto == null) return null;
        return new UserSummary(dto.userId, dto.fullName, dto.avatarUrl);
    }

    public static List<UserSummary> toDomainList(List<UserSummaryResponse> dtos) {
        if (dtos == null) return Collections.emptyList();
        return dtos.stream()
                .map(SocialMapper::toDomain)
                .collect(Collectors.toList());
    }
}
