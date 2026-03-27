package com.walkmate.application.profile;

import com.walkmate.domain.profile.Profile;
import com.walkmate.domain.profile.ProfileErrorCode;
import com.walkmate.domain.profile.ProfileRepository;
import com.walkmate.domain.shared.exception.DomainException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileCommandService {
    private static final int MIN_TAGS_REQUIRED = 3;

    private final ProfileRepository profileRepository;

    public ProfileCommandService(ProfileRepository profileRepository) {
        this.profileRepository = profileRepository;
    }

    @Transactional
    public Profile setupProfile(Profile profile) {
        if (profile.getTags().size() < MIN_TAGS_REQUIRED) {
            throw new DomainException(
                    ProfileErrorCode.PROFILE_TAGS_MIN_REQUIRED.name(),
                    "At least " + MIN_TAGS_REQUIRED + " tags are required"
            );
        }

        try {
            return profileRepository.upsert(profile);
        } catch (IllegalArgumentException e) {
            throw new DomainException(ProfileErrorCode.PROFILE_INVALID_NAME.name(), e.getMessage());
        }
    }
}
