package com.walkmate.domain.walksession;

public class SessionSummary {
    private final String sessionId;
    private final WalkSession.Status status;
    private final String partnerId;
    private final String scheduledStart;
    private final double totalDistanceKm;
    private final int durationMinutes;
    private final boolean isReviewed;

    public SessionSummary(String sessionId, WalkSession.Status status, String partnerId,
                          String scheduledStart, double totalDistanceKm,
                          int durationMinutes, boolean isReviewed) {
        this.sessionId = sessionId;
        this.status = status;
        this.partnerId = partnerId;
        this.scheduledStart = scheduledStart;
        this.totalDistanceKm = totalDistanceKm;
        this.durationMinutes = durationMinutes;
        this.isReviewed = isReviewed;
    }

    public String getSessionId() { return sessionId; }
    public WalkSession.Status getStatus() { return status; }
    public String getPartnerId() { return partnerId; }
    public String getScheduledStart() { return scheduledStart; }
    public double getTotalDistanceKm() { return totalDistanceKm; }
    public int getDurationMinutes() { return durationMinutes; }
    public boolean isReviewed() { return isReviewed; }
}
