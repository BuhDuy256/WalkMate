package com.walkmate.ui.badge;

import java.util.List;

public class BadgeUiState {

    // ── Badge item ────────────────────────────────────────────────────────────

    public static class BadgeItem {
        public final String  badgeName;
        public final String  displayName;
        public final String  description;
        public final boolean earned;
        public final String  earnedDate;   // null when not earned
        public final String  rarity;       // "common", "rare", "epic", "legendary"
        public final String  category;
        public final int     progressPct;  // 0–100, used when !earned
        public final String  progressLabel; // e.g. "7 / 10 walks"

        public BadgeItem(String badgeName, String displayName, String description,
                         boolean earned, String earnedDate, String rarity, String category,
                         int progressPct, String progressLabel) {
            this.badgeName     = badgeName;
            this.displayName   = displayName;
            this.description   = description;
            this.earned        = earned;
            this.earnedDate    = earnedDate;
            this.rarity        = rarity;
            this.category      = category;
            this.progressPct   = progressPct;
            this.progressLabel = progressLabel;
        }
    }

    // ── Category section ──────────────────────────────────────────────────────

    public static class BadgeCategory {
        public final String          title;
        public final List<BadgeItem> badges;

        public BadgeCategory(String title, List<BadgeItem> badges) {
            this.title  = title;
            this.badges = badges;
        }
    }

    // ── Screen state ──────────────────────────────────────────────────────────

    private final boolean              isLoading;
    private final String               error;
    private final List<BadgeCategory>  categories;
    private final int                  earnedCount;
    private final int                  totalCount;

    public BadgeUiState(boolean isLoading, String error,
                        List<BadgeCategory> categories,
                        int earnedCount, int totalCount) {
        this.isLoading   = isLoading;
        this.error       = error;
        this.categories  = categories;
        this.earnedCount = earnedCount;
        this.totalCount  = totalCount;
    }

    public static BadgeUiState loading() {
        return new BadgeUiState(true, null, null, 0, 0);
    }

    public static BadgeUiState error(String message) {
        return new BadgeUiState(false, message, null, 0, 0);
    }

    public boolean isLoading()              { return isLoading; }
    public String  getError()               { return error; }
    public List<BadgeCategory> getCategories() { return categories; }
    public int     getEarnedCount()         { return earnedCount; }
    public int     getTotalCount()          { return totalCount; }

    public int getProgressPct() {
        if (totalCount == 0) return 0;
        return Math.round((earnedCount * 100f) / totalCount);
    }
}
