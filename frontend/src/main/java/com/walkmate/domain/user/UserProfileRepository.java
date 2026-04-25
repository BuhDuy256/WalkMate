package com.walkmate.domain.user;

import com.walkmate.domain.shared.DomainCallback;

import java.util.List;

public interface UserProfileRepository {

    /** Fetches the authenticated user's own profile. */
    void getMyProfile(DomainCallback<UserProfile> callback);

    /** Fetches any user's public profile by their userId. */
    void getProfile(String userId, DomainCallback<UserProfile> callback);

    /** Returns all master tags available for user selection. */
    void getMasterTags(DomainCallback<List<ProfileTagMaster>> callback);

    /**
     * Updates the authenticated user's profile fields.
     * tagIds is a list of UUID strings from profile_tag_master.
     */
    void updateProfile(String fullName, String gender, String dateOfBirth,
                       String bio, int searchRadius, List<String> tagIds,
                       DomainCallback<UserProfile> callback);

    /** Uploads an avatar image (byte array) and returns the new avatar URL. */
    void uploadAvatar(byte[] imageBytes, String filename, String mimeType,
                      DomainCallback<String> callback);
}
