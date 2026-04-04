package com.walkmate.data.mapper;

import com.walkmate.data.datasource.remote.dto.ProfileResponseDto;
import com.walkmate.domain.profile.InfoVisibilityMode;
import com.walkmate.domain.profile.Profile;
import com.walkmate.domain.profile.ProfileMode;
import com.walkmate.domain.profile.ProfileTag;

import java.util.List;
import java.util.stream.Collectors;

public class ProfileDtoToDomainMapper {
    public Profile mapToDomain(ProfileResponseDto dto) {
        List<ProfileTag> tags = dto.getTags() == null
                ? List.of()
                : dto.getTags().stream()
                .map(ProfileTag::fromCode)
                .collect(Collectors.toList());

        return new Profile(
                dto.getUserId(),
                dto.getFullName(),
                dto.getCity(),
                dto.getAvatarUrl(),
                dto.getBio(),
                null,
                null,
                ProfileMode.fromCode(dto.getProfileMode()),
                InfoVisibilityMode.fromCode(dto.getInfoVisibilityMode()),
                tags,
                dto.getEmail(),
                dto.getPhone()
        );
    }
}
