package com.walkmate.domain.gamification;

public class UserBadge {

    private final String badgeName;
    private final String awardedAt;

    public UserBadge(String badgeName, String awardedAt) {
        this.badgeName = badgeName;
        this.awardedAt = awardedAt;
    }

    public String getBadgeName() { return badgeName; }
    public String getAwardedAt() { return awardedAt; }
}
