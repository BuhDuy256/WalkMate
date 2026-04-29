package com.walkmate.ui.home;

import java.util.Collections;
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
        public final String partnerAvatarUrl;
        public final String timeAndPlace;
        public final String statusLabel;

        public UpcomingSessionSnapshot(String sessionId, String partnerName,
                                       String partnerAvatarUrl, String timeAndPlace,
                                       String statusLabel) {
            this.sessionId        = sessionId;
            this.partnerName      = partnerName;
            this.partnerAvatarUrl = partnerAvatarUrl;
            this.timeAndPlace     = timeAndPlace;
            this.statusLabel      = statusLabel;
        }
    }

    public static class RecentMateSnapshot {
        public final String partnerName;
        public final String partnerAvatarUrl;
        public final double meetingLat;
        public final double meetingLng;
        public final long   scheduledAtMs;

        public RecentMateSnapshot(String partnerName, String partnerAvatarUrl,
                                   double meetingLat, double meetingLng,
                                   long scheduledAtMs) {
            this.partnerName      = partnerName;
            this.partnerAvatarUrl = partnerAvatarUrl;
            this.meetingLat       = meetingLat;
            this.meetingLng       = meetingLng;
            this.scheduledAtMs    = scheduledAtMs;
        }
    }

    // ── Fields ────────────────────────────────────────────────────────────────

    private final boolean isLoading;
    private final String greetingName;
    private final String locationName;
    private final boolean hasUnreadNotification;
    private final UpcomingSessionSnapshot upcomingSession;
    private final double totalDistanceKm;
    private final int completedSessions;
    private final String error;
    private final List<RecentMateSnapshot> recentMates;

    // ── Constructor ───────────────────────────────────────────────────────────

    public HomeDashboardUiState(boolean isLoading, String greetingName, String locationName,
                                 boolean hasUnreadNotification,
                                 UpcomingSessionSnapshot upcomingSession,
                                 double totalDistanceKm, int completedSessions,
                                 String error, List<RecentMateSnapshot> recentMates) {
        this.isLoading              = isLoading;
        this.greetingName           = greetingName;
        this.locationName           = locationName;
        this.hasUnreadNotification  = hasUnreadNotification;
        this.upcomingSession        = upcomingSession;
        this.totalDistanceKm        = totalDistanceKm;
        this.completedSessions      = completedSessions;
        this.error                  = error;
        this.recentMates            = recentMates != null ? recentMates : Collections.emptyList();
    }

    // ── Static factories ──────────────────────────────────────────────────────

    public static HomeDashboardUiState loading() {
        return new HomeDashboardUiState(
                true, null, null, false,
                null, 0.0, 0, null, Collections.emptyList());
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public boolean isLoading()                              { return isLoading; }
    public String getGreetingName()                         { return greetingName; }
    public String getLocationName()                         { return locationName; }
    public boolean hasUnreadNotification()                  { return hasUnreadNotification; }
    public UpcomingSessionSnapshot getUpcomingSession()     { return upcomingSession; }
    public double getTotalDistanceKm()                      { return totalDistanceKm; }
    public int getCompletedSessions()                       { return completedSessions; }
    public String getError()                                { return error; }
    public List<RecentMateSnapshot> getRecentMates()        { return recentMates; }
}
