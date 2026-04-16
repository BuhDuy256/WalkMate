package com.walkmate.data.datasource.remote.dto.response.session;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class SessionRouteResponse {

    @SerializedName("user_a_polylines")
    private List<String> userAPolylines;

    @SerializedName("user_b_polylines")
    private List<String> userBPolylines;

    @SerializedName("total_distance_km")
    private double totalDistanceKm;

    @SerializedName("duration_minutes")
    private int durationMinutes;

    public SessionRouteResponse(List<String> userAPolylines,
                                List<String> userBPolylines,
                                double totalDistanceKm,
                                int durationMinutes) {
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
