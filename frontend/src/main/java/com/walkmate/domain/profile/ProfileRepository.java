package com.walkmate.domain.profile;

import java.util.UUID;

public interface ProfileRepository {
    String uploadAvatar(UUID userId, ProfileAvatarUpload avatarUpload) throws ProfileException;
    Profile setupProfile(Profile profile) throws ProfileException;
    Profile getProfile(UUID userId, UUID viewerId) throws ProfileException;
}
