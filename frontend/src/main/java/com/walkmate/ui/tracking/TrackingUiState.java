package com.walkmate.ui.tracking;

import com.google.android.gms.maps.model.LatLng;
import java.util.List;

public class TrackingUiState {
    private final boolean isTracking;
    private final List<LatLng> pathPoints;
    private final float totalDistanceMeters;

    public TrackingUiState(boolean isTracking, List<LatLng> pathPoints, float totalDistanceMeters) {
        this.isTracking = isTracking;
        this.pathPoints = pathPoints;
        this.totalDistanceMeters = totalDistanceMeters;
    }

    public boolean isTracking() { return isTracking; }
    public List<LatLng> getPathPoints() { return pathPoints; }
    public float getTotalDistanceMeters() { return totalDistanceMeters; }
}
