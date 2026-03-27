package com.walkmate.domain.profile;

import java.util.UUID;

public interface ProfileRepository {
    Profile setupProfile(Profile profile) throws ProfileException;
    Profile getProfile(UUID userId, UUID viewerId) throws ProfileException;
}
