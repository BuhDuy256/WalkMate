package com.walkmate.domain.social;

/**
 * Lightweight user representation used in friends / social lists.
 * For the full profile, use UserProfileRepository.getProfile().
 */
public class UserSummary {

    private final String userId;
    private final String fullName;
    private final String avatarUrl;
    // "NONE" | "PENDING_SENT" | "PENDING_RECEIVED" | "FRIENDS"
    private final String friendshipStatus;

    public UserSummary(String userId, String fullName, String avatarUrl, String friendshipStatus) {
        this.userId           = userId;
        this.fullName         = fullName;
        this.avatarUrl        = avatarUrl;
        this.friendshipStatus = friendshipStatus;
    }

    public String getUserId()           { return userId; }
    public String getFullName()         { return fullName; }
    public String getAvatarUrl()        { return avatarUrl; }
    public String getFriendshipStatus() { return friendshipStatus; }
}
