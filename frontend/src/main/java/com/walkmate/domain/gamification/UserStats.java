package com.walkmate.domain.gamification;

public class UserStats {

    private final String userId;
    private final int    totalPoints;
    private final double totalDistanceKm;
    private final int    completedSessions;
    private final int    trustScore;

    public UserStats(String userId, int totalPoints, double totalDistanceKm,
                     int completedSessions, int trustScore) {
        this.userId            = userId;
        this.totalPoints       = totalPoints;
        this.totalDistanceKm   = totalDistanceKm;
        this.completedSessions = completedSessions;
        this.trustScore        = trustScore;
    }

    public String getUserId()           { return userId; }
    public int    getTotalPoints()      { return totalPoints; }
    public double getTotalDistanceKm()  { return totalDistanceKm; }
    public int    getCompletedSessions(){ return completedSessions; }
    public int    getTrustScore()       { return trustScore; }
}
