package com.walkmate.domain.social;

import java.util.List;

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
    private final String bio;
    private final List<String> tags;
    private final String pendingRequestId; // non-null only when friendshipStatus == "PENDING_RECEIVED"

    public UserSummary(String userId, String fullName, String avatarUrl, String friendshipStatus,
                       String bio, List<String> tags, String pendingRequestId) {
        this.userId            = userId;
        this.fullName          = fullName;
        this.avatarUrl         = avatarUrl;
        this.friendshipStatus  = friendshipStatus;
        this.bio               = bio;
        this.tags              = tags;
        this.pendingRequestId  = pendingRequestId;
    }

    public String getUserId()            { return userId; }
    public String getFullName()          { return fullName; }
    public String getAvatarUrl()         { return avatarUrl; }
    public String getFriendshipStatus()  { return friendshipStatus; }
    public String getBio()               { return bio; }
    public List<String> getTags()        { return tags; }
    public String getPendingRequestId()  { return pendingRequestId; }
}
