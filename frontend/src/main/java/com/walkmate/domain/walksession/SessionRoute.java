package com.walkmate.domain.walksession;

import java.util.List;

public class SessionRoute {
    private final List<String> userAPolylines;
    private final List<String> userBPolylines;
    private final double totalDistanceKm;
    private final int durationMinutes;

    public SessionRoute(List<String> userAPolylines, List<String> userBPolylines,
                        double totalDistanceKm, int durationMinutes) {
        this.userAPolylines = userAPolylines;
        this.userBPolylines = userBPolylines;
        this.totalDistanceKm = totalDistanceKm;
        this.durationMinutes = durationMinutes;
    }

    public List<String> getUserAPolylines() { return userAPolylines; }
    public List<String> getUserBPolylines() { return userBPolylines; }
    public double getTotalDistanceKm() { return totalDistanceKm; }
    public int getDurationMinutes() { return durationMinutes; }
}
