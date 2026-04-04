package com.walkmate.data.mapper;

import com.walkmate.data.datasource.remote.dto.ProfileResponseDto;
import com.walkmate.domain.profile.InfoVisibilityMode;
import com.walkmate.domain.profile.Profile;
import com.walkmate.domain.profile.ProfileMode;
import com.walkmate.domain.profile.ProfileTag;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ProfileDtoToDomainMapper {
    public Profile mapToDomain(ProfileResponseDto dto) {
        List<String> mergedTagCodes = mergeTagCodes(dto);
        List<ProfileTag> tags = mergedTagCodes.stream()
                .map(this::safeTagFromCode)
                .filter(tag -> tag != null)
                .collect(Collectors.toList());

        String resolvedProfileMode = dto.getProfileVisibility() != null
                ? dto.getProfileVisibility()
                : dto.getProfileMode();

        return new Profile(
                dto.getUserId(),
                dto.getFullName(),
                dto.getCity(),
                dto.getAvatarUrl(),
                dto.getBio(),
                parseDate(dto.getDateOfBirth()),
                dto.getGender(),
                ProfileMode.fromCode(resolvedProfileMode),
                InfoVisibilityMode.fromCode(dto.getInfoVisibilityMode()),
                tags,
                dto.getEmail(),
                dto.getPhone()
        );
    }

    private List<String> mergeTagCodes(ProfileResponseDto dto) {
        List<String> merged = new ArrayList<>();

        if (dto.getInterests() != null) {
            merged.addAll(dto.getInterests());
        }
        if (dto.getWalkVibes() != null) {
            merged.addAll(dto.getWalkVibes());
        }
        if (dto.getBestTimeToWalk() != null) {
            merged.addAll(dto.getBestTimeToWalk());
        }

        if (merged.isEmpty() && dto.getTags() != null) {
            merged.addAll(dto.getTags());
        }

        return merged;
    }

    private ProfileTag safeTagFromCode(String code) {
        try {
            return ProfileTag.fromCode(code);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return LocalDate.parse(value);
    }
}
