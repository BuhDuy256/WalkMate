package com.walkmate.domain.gamification;

public class LeaderboardEntry {

    private final int    rank;
    private final String userId;
    private final String fullName;
    private final int    totalPoints;
    private final double totalDistanceKm;
    private final int    completedSessions;
    private final int    trustScore;

    public LeaderboardEntry(int rank, String userId, String fullName, int totalPoints,
                            double totalDistanceKm, int completedSessions, int trustScore) {
        this.rank              = rank;
        this.userId            = userId;
        this.fullName          = fullName;
        this.totalPoints       = totalPoints;
        this.totalDistanceKm   = totalDistanceKm;
        this.completedSessions = completedSessions;
        this.trustScore        = trustScore;
    }

    public int    getRank()              { return rank; }
    public String getUserId()            { return userId; }
    public String getFullName()          { return fullName; }
    public int    getTotalPoints()       { return totalPoints; }
    public double getTotalDistanceKm()   { return totalDistanceKm; }
    public int    getCompletedSessions() { return completedSessions; }
    public int    getTrustScore()        { return trustScore; }
}
