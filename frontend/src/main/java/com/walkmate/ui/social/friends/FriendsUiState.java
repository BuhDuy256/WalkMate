package com.walkmate.ui.social.friends;

import com.walkmate.domain.social.FriendRequest;
import com.walkmate.domain.social.UserSummary;

import java.util.Collections;
import java.util.List;

/**
 * Immutable state snapshot for the Friends screen.
 * incomingBadgeCount drives the badge on the Incoming tab header.
 */
public class FriendsUiState {

    private final boolean isLoading;
    private final String error;
    private final List<UserSummary> friends;
    private final List<FriendRequest> incomingRequests;
    private final List<FriendRequest> outgoingRequests;
    private final int incomingBadgeCount;

    public FriendsUiState(boolean isLoading, String error,
                          List<UserSummary> friends,
                          List<FriendRequest> incomingRequests,
                          List<FriendRequest> outgoingRequests) {
        this.isLoading         = isLoading;
        this.error             = error;
        this.friends           = friends != null ? friends : Collections.emptyList();
        this.incomingRequests  = incomingRequests != null ? incomingRequests : Collections.emptyList();
        this.outgoingRequests  = outgoingRequests != null ? outgoingRequests : Collections.emptyList();
        this.incomingBadgeCount = this.incomingRequests.size();
    }

    public static FriendsUiState loading() {
        return new FriendsUiState(true, null, null, null, null);
    }

    public static FriendsUiState error(String msg) {
        return new FriendsUiState(false, msg, null, null, null);
    }

    public boolean isLoading()                        { return isLoading; }
    public String getError()                          { return error; }
    public List<UserSummary> getFriends()             { return friends; }
    public List<FriendRequest> getIncomingRequests()  { return incomingRequests; }
    public List<FriendRequest> getOutgoingRequests()  { return outgoingRequests; }
    public int getIncomingBadgeCount()                { return incomingBadgeCount; }
    public int getFriendsCount()                      { return friends.size(); }
    public int getOutgoingCount()                     { return outgoingRequests.size(); }
}
