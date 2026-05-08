package com.walkmate.ui.profile.publicprofile;

import com.walkmate.domain.gamification.UserStats;
import com.walkmate.domain.review.WalkReview;
import com.walkmate.domain.social.UserSummary;
import com.walkmate.domain.walkpost.WalkPost;
import com.walkmate.ui.profile.ProfileUiState.Badge;

import java.util.Collections;
import java.util.List;

public class PublicProfileUiState {

    private final boolean isLoading;
    private final String error;
    private final UserSummary profile;
    private final List<Badge> badges;
    private final UserStats stats;
    private final List<WalkReview> reviews;
    private final List<WalkPost> posts;
    private final String friendshipStatus;
    private final boolean isSelf;

    public PublicProfileUiState(boolean isLoading, String error, UserSummary profile,
                                List<Badge> badges, UserStats stats, List<WalkReview> reviews,
                                List<WalkPost> posts,
                                String friendshipStatus, boolean isSelf) {
        this.isLoading        = isLoading;
        this.error            = error;
        this.profile          = profile;
        this.badges           = badges;
        this.stats            = stats;
        this.reviews          = reviews;
        this.posts            = posts != null ? posts : Collections.emptyList();
        this.friendshipStatus = friendshipStatus;
        this.isSelf           = isSelf;
    }

    public static PublicProfileUiState loading() {
        return new PublicProfileUiState(
                true, null, null,
                Collections.emptyList(), null, Collections.emptyList(),
                Collections.emptyList(),
                "NONE", false);
    }

    public static PublicProfileUiState error(String message) {
        return new PublicProfileUiState(
                false, message, null,
                Collections.emptyList(), null, Collections.emptyList(),
                Collections.emptyList(),
                "NONE", false);
    }

    public boolean isLoading()             { return isLoading; }
    public String getError()               { return error; }
    public UserSummary getProfile()        { return profile; }
    public List<Badge> getBadges()         { return badges; }
    public UserStats getStats()            { return stats; }
    public List<WalkReview> getReviews()   { return reviews; }
    public List<WalkPost> getPosts()       { return posts; }
    public String getFriendshipStatus()    { return friendshipStatus; }
    public boolean isSelf()               { return isSelf; }
    public boolean isFriend()             { return "FRIENDS".equals(friendshipStatus); }
}
