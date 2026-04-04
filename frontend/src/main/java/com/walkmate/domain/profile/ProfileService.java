package com.walkmate.domain.profile;

public class ProfileService {
    private final ProfileRepository repository;

    public ProfileService(ProfileRepository repository) {
        this.repository = repository;
    }

    public Profile setupProfile(Profile profile) throws ProfileException {
        if (profile.getFullName() == null || profile.getFullName().trim().isEmpty()) {
            throw new ProfileException(ProfileErrorCode.PROFILE_INVALID_NAME, "Display name is required");
        }
        return repository.setupProfile(profile);
    }

    public Profile getProfile(java.util.UUID userId, java.util.UUID viewerId) throws ProfileException {
        return repository.getProfile(userId, viewerId);
    }
}
