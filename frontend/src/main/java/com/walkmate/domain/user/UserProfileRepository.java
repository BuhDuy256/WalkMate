package com.walkmate.domain.user;

import com.walkmate.domain.shared.DomainCallback;

import java.util.List;

public interface UserProfileRepository {

    /** Fetches the authenticated user's own profile. */
    void getMyProfile(DomainCallback<UserProfile> callback);

    /** Fetches any user's public profile by their userId. */
    void getProfile(String userId, DomainCallback<UserProfile> callback);

    /** Updates the authenticated user's profile fields. */
    void updateProfile(String fullName, String gender, String dateOfBirth,
                       String bio, int searchRadius, List<String> tags,
                       DomainCallback<UserProfile> callback);

    /** Uploads an avatar image (byte array) and returns the new avatar URL. */
    void uploadAvatar(byte[] imageBytes, String filename, String mimeType,
                      DomainCallback<String> callback);
}
