package com.walkmate.ui.gamification.leaderboard;

import com.walkmate.domain.gamification.LeaderboardEntry;

import java.util.List;

public class LeaderboardUiState {

    private final boolean              isLoading;
    private final String               error;
    private final List<LeaderboardEntry> entries;
    private final String               lastUpdatedLabel; // non-null when showing cached data banner

    public LeaderboardUiState(boolean isLoading, String error,
                              List<LeaderboardEntry> entries, String lastUpdatedLabel) {
        this.isLoading        = isLoading;
        this.error            = error;
        this.entries          = entries;
        this.lastUpdatedLabel = lastUpdatedLabel;
    }

    public boolean               isLoading()          { return isLoading; }
    public String                getError()            { return error; }
    public List<LeaderboardEntry> getEntries()         { return entries; }
    public String                getLastUpdatedLabel() { return lastUpdatedLabel; }
}
