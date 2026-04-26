package com.walkmate.domain.social;

import com.walkmate.domain.shared.DomainCallback;

import java.util.List;

public interface SocialRepository {

    // ── Friends ───────────────────────────────────────────────────────────────

    void getFriends(DomainCallback<List<UserSummary>> callback);

    void sendFriendRequest(String userId, DomainCallback<Void> callback);

    void acceptFriendRequest(String requestId, DomainCallback<Void> callback);

    void declineFriendRequest(String requestId, DomainCallback<Void> callback);

    void getIncomingRequests(DomainCallback<List<FriendRequest>> callback);

    void getOutgoingRequests(DomainCallback<List<FriendRequest>> callback);

    void removeFriend(String userId, DomainCallback<Void> callback);

    void cancelFriendRequest(String requestId, DomainCallback<Void> callback);

    void getPublicProfile(String userId, DomainCallback<UserSummary> callback);

    // ── Block ─────────────────────────────────────────────────────────────────

    void block(String targetUserId, DomainCallback<Void> callback);

    void unblock(String targetUserId, DomainCallback<Void> callback);

    void getBlockedUsers(DomainCallback<List<UserSummary>> callback);
}
