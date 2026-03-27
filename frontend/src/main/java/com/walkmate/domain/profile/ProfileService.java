package com.walkmate.domain.profile;

public class ProfileService {
    private static final int MIN_TAGS = 3;

    private final ProfileRepository repository;

    public ProfileService(ProfileRepository repository) {
        this.repository = repository;
    }

    public Profile setupProfile(Profile profile) throws ProfileException {
        if (profile.getFullName() == null || profile.getFullName().trim().isEmpty()) {
            throw new ProfileException(ProfileErrorCode.PROFILE_INVALID_NAME, "Display name is required");
        }
        if (profile.getTags() == null || profile.getTags().size() < MIN_TAGS) {
            throw new ProfileException(
                    ProfileErrorCode.PROFILE_TAGS_MIN_REQUIRED,
                    "Please select at least " + MIN_TAGS + " tags"
            );
        }
        return repository.setupProfile(profile);
    }

    public Profile getProfile(java.util.UUID userId, java.util.UUID viewerId) throws ProfileException {
        return repository.getProfile(userId, viewerId);
    }
}
