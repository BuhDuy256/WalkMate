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
    private final int    trustScore;
    // "NONE" | "PENDING_SENT" | "PENDING_RECEIVED" | "FRIENDS"
    private final String friendshipStatus;
    private final String bio;
    private final List<String> tags;
    private final String pendingRequestId; // non-null only when friendshipStatus == "PENDING_RECEIVED"
    private final String lastActiveAt;    // non-null only when caller is an accepted friend

    public UserSummary(String userId, String fullName, String avatarUrl, int trustScore,
                       String friendshipStatus,
                       String bio, List<String> tags, String pendingRequestId, String lastActiveAt) {
        this.userId            = userId;
        this.fullName          = fullName;
        this.avatarUrl         = avatarUrl;
        this.trustScore        = trustScore;
        this.friendshipStatus  = friendshipStatus;
        this.bio               = bio;
        this.tags              = tags;
        this.pendingRequestId  = pendingRequestId;
        this.lastActiveAt      = lastActiveAt;
    }

    public String getUserId()            { return userId; }
    public String getFullName()          { return fullName; }
    public String getAvatarUrl()         { return avatarUrl; }
    public int    getTrustScore()        { return trustScore; }
    public String getFriendshipStatus()  { return friendshipStatus; }
    public String getBio()               { return bio; }
    public List<String> getTags()        { return tags; }
    public String getPendingRequestId()  { return pendingRequestId; }
    public String getLastActiveAt()      { return lastActiveAt; }
}
