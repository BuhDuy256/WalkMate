package com.walkmate.domain.social;

import com.walkmate.domain.shared.DomainCallback;

import java.util.List;

public interface SocialRepository {

    // ── Follow ────────────────────────────────────────────────────────────────

    void follow(String targetUserId, DomainCallback<Void> callback);

    void unfollow(String targetUserId, DomainCallback<Void> callback);

    void getFollowers(String userId, DomainCallback<List<UserSummary>> callback);

    void getFollowing(String userId, DomainCallback<List<UserSummary>> callback);

    // ── Block ─────────────────────────────────────────────────────────────────

    void block(String targetUserId, DomainCallback<Void> callback);

    void unblock(String targetUserId, DomainCallback<Void> callback);
}
