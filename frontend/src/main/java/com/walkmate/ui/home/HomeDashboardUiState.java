package com.walkmate.ui.home;

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

    // ── Fields ────────────────────────────────────────────────────────────────

    private final boolean isLoading;
    private final String greetingName;            // "Alex" → rendered as "Hi, Alex! 👋"
    private final String locationName;            // "Ho Chi Minh City"
    private final boolean hasUnreadNotification;
    private final UpcomingSessionSnapshot upcomingSession;  // null when no session
    private final double totalDistanceKm;
    private final int completedSessions;
    private final String error;                   // non-null when a one-time error must be shown

    // ── Constructor ───────────────────────────────────────────────────────────

    public HomeDashboardUiState(
            boolean isLoading,
            String greetingName,
            String locationName,
            boolean hasUnreadNotification,
            UpcomingSessionSnapshot upcomingSession,
            double totalDistanceKm,
            int completedSessions,
            String error) {
        this.isLoading = isLoading;
        this.greetingName = greetingName;
        this.locationName = locationName;
        this.hasUnreadNotification = hasUnreadNotification;
        this.upcomingSession = upcomingSession;
        this.totalDistanceKm = totalDistanceKm;
        this.completedSessions = completedSessions;
        this.error = error;
    }

    // ── Static factories ──────────────────────────────────────────────────────

    /** Returns a loading placeholder state. Displayed while data is being fetched. */
    public static HomeDashboardUiState loading() {
        return new HomeDashboardUiState(
                true, null, null, false,
                null, 0.0, 0, null);
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public boolean isLoading()                        { return isLoading; }
    public String getGreetingName()                   { return greetingName; }
    public String getLocationName()                   { return locationName; }
    public boolean hasUnreadNotification()            { return hasUnreadNotification; }
    public UpcomingSessionSnapshot getUpcomingSession() { return upcomingSession; }
    public double getTotalDistanceKm()                { return totalDistanceKm; }
    public int getCompletedSessions()                 { return completedSessions; }
    public String getError()                         { return error; }
}
