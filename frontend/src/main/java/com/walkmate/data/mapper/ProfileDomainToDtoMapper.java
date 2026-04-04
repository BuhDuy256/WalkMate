package com.walkmate.data.mapper;

import com.walkmate.data.datasource.remote.dto.SetupProfileRequestDto;
import com.walkmate.domain.profile.Profile;
import com.walkmate.domain.profile.ProfileTag;

import java.util.List;
import java.util.stream.Collectors;

public class ProfileDomainToDtoMapper {

    public SetupProfileRequestDto mapToDto(Profile profile) {
        List<String> tagCodes = profile.getTags().stream()
                .map(ProfileTag::name)
                .collect(Collectors.toList());

        return new SetupProfileRequestDto(
                profile.getUserId(),
                profile.getFullName(),
                profile.getCity(),
                profile.getAvatarUrl(),
                profile.getBio(),
                profile.getProfileMode().name(),
                tagCodes
        );
    }
}
