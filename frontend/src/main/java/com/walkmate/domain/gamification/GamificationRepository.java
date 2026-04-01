package com.walkmate.domain.gamification;

import com.walkmate.domain.shared.DomainCallback;

import java.util.List;

public interface GamificationRepository {

    void getBadges(String userId, DomainCallback<List<UserBadge>> callback);

    void getStats(String userId, DomainCallback<UserStats> callback);

    void getLeaderboard(DomainCallback<List<LeaderboardEntry>> callback);
}
