package com.walkmate.domain.gamification;

public class UserBadge {

    private final String badgeName;
    private final String displayName;
    private final String description;
    private final String iconUrl;
    private final String awardedAt;

    public UserBadge(String badgeName, String displayName, String description,
                     String iconUrl, String awardedAt) {
        this.badgeName   = badgeName;
        this.displayName = displayName;
        this.description = description;
        this.iconUrl     = iconUrl;
        this.awardedAt   = awardedAt;
    }

    public String getBadgeName()   { return badgeName; }
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public String getIconUrl()     { return iconUrl; }
    public String getAwardedAt()   { return awardedAt; }
}
