package com.walkmate.ui.home;

import java.util.List;

/**
 * Immutable snapshot of all data rendered on the Home Dashboard.
 *
 * Rule: no setters. The ViewModel calls postValue(new HomeDashboardUiState(...))
 * to push a new snapshot whenever state changes.
 */
public class HomeDashboardUiState {

    // ── Inner snapshots ───────────────────────────────────────────────────────

    public static class UpcomingSessionSnapshot {
        public final String sessionId;
        public final String partnerName;
        public final String partnerAvatarUrl;  // null → show placeholder
        public final String timeAndPlace;       // e.g. "14:00 · Tao Dan Park"
        public final String statusLabel;        // e.g. "Confirmed"

        public UpcomingSessionSnapshot(
                String sessionId,
                String partnerName,
                String partnerAvatarUrl,
                String timeAndPlace,
                String statusLabel) {
            this.sessionId = sessionId;
            this.partnerName = partnerName;
            this.partnerAvatarUrl = partnerAvatarUrl;
            this.timeAndPlace = timeAndPlace;
            this.statusLabel = statusLabel;
        }
    }

    public static class QuickInviteUser {
        public final String userId;
        public final String displayName;
        public final String avatarUrl;  // null → show placeholder

        public QuickInviteUser(String userId, String displayName, String avatarUrl) {
            this.userId = userId;
            this.displayName = displayName;
            this.avatarUrl = avatarUrl;
        }
    }

    // ── Fields ────────────────────────────────────────────────────────────────

    private final boolean isLoading;
    private final String greetingName;            // "Alex" → rendered as "Hi, Alex! 👋"
    private final String locationName;            // "Ho Chi Minh City"
    private final boolean hasUnreadNotification;
    private final int streakDays;                 // current streak days
    private final int streakGoal;                 // target (e.g. 7)
    private final int nearbyHotspotCount;         // inserted into hero subtitle format string
    private final UpcomingSessionSnapshot upcomingSession;  // null when no session
    private final List<QuickInviteUser> quickInviteList;
    private final double weeklyDistanceKm;
    private final int weeklySessionCount;
    private final String error;                   // non-null when a one-time error must be shown

    // ── Constructor ───────────────────────────────────────────────────────────

    public HomeDashboardUiState(
            boolean isLoading,
            String greetingName,
            String locationName,
            boolean hasUnreadNotification,
            int streakDays,
            int streakGoal,
            int nearbyHotspotCount,
            UpcomingSessionSnapshot upcomingSession,
            List<QuickInviteUser> quickInviteList,
            double weeklyDistanceKm,
            int weeklySessionCount,
            String error) {
        this.isLoading = isLoading;
        this.greetingName = greetingName;
        this.locationName = locationName;
        this.hasUnreadNotification = hasUnreadNotification;
        this.streakDays = streakDays;
        this.streakGoal = streakGoal;
        this.nearbyHotspotCount = nearbyHotspotCount;
        this.upcomingSession = upcomingSession;
        this.quickInviteList = quickInviteList;
        this.weeklyDistanceKm = weeklyDistanceKm;
        this.weeklySessionCount = weeklySessionCount;
        this.error = error;
    }

    // ── Static factories ──────────────────────────────────────────────────────

    /** Returns a loading placeholder state. Displayed while data is being fetched. */
    public static HomeDashboardUiState loading() {
        return new HomeDashboardUiState(
                true, null, null, false,
                0, 7, 0, null, null,
                0.0, 0, null);
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public boolean isLoading()                        { return isLoading; }
    public String getGreetingName()                   { return greetingName; }
    public String getLocationName()                   { return locationName; }
    public boolean hasUnreadNotification()            { return hasUnreadNotification; }
    public int getStreakDays()                        { return streakDays; }
    public int getStreakGoal()                        { return streakGoal; }
    public int getNearbyHotspotCount()               { return nearbyHotspotCount; }
    public UpcomingSessionSnapshot getUpcomingSession() { return upcomingSession; }
    public List<QuickInviteUser> getQuickInviteList() { return quickInviteList; }
    public double getWeeklyDistanceKm()              { return weeklyDistanceKm; }
    public int getWeeklySessionCount()               { return weeklySessionCount; }
    public String getError()                         { return error; }
}
