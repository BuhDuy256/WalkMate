package com.walkmate.domain.social;

/**
 * Lightweight user representation used in follower / following lists.
 * For the full profile, use UserProfileRepository.getProfile().
 */
public class UserSummary {

    private final String userId;
    private final String fullName;
    private final String avatarUrl;

    public UserSummary(String userId, String fullName, String avatarUrl) {
        this.userId    = userId;
        this.fullName  = fullName;
        this.avatarUrl = avatarUrl;
    }

    public String getUserId()   { return userId; }
    public String getFullName() { return fullName; }
    public String getAvatarUrl(){ return avatarUrl; }
}
