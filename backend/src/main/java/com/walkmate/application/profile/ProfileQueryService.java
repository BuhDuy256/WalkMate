package com.walkmate.application.profile;

import com.walkmate.domain.profile.InfoVisibilityMode;
import com.walkmate.domain.profile.Profile;
import com.walkmate.domain.profile.ProfileErrorCode;
import com.walkmate.domain.profile.ProfileMode;
import com.walkmate.domain.profile.ProfileRepository;
import com.walkmate.domain.shared.exception.DomainException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class ProfileQueryService {
    private final ProfileRepository profileRepository;

    public ProfileQueryService(ProfileRepository profileRepository) {
        this.profileRepository = profileRepository;
    }

    public Profile getProfileForViewer(UUID profileUserId, UUID viewerId) {
        Profile profile = profileRepository.findByUserId(profileUserId)
                .orElseThrow(() -> new DomainException(
                        ProfileErrorCode.PROFILE_NOT_FOUND.name(),
                        "Profile not found"
                ));

        boolean isOwner = viewerId != null && viewerId.equals(profileUserId);

        if (!isOwner && profile.getProfileMode() == ProfileMode.PRIVATE) {
            throw new DomainException(
                    ProfileErrorCode.PROFILE_PRIVATE.name(),
                    "This profile is private"
            );
        }

        if (!isOwner && profile.getInfoVisibilityMode() == InfoVisibilityMode.PRIVATE) {
            return profile.withContactInfo("", "");
        }

        return profile;
    }
}
