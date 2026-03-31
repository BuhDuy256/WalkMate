package com.walkmate.ui.tracking;

import com.google.android.gms.maps.model.LatLng;
import com.walkmate.domain.tracking.WalkState;

import java.util.Collections;
import java.util.List;

/**
 * Immutable snapshot of everything the TrackingScreenActivity needs to render.
 *
 * Single source of truth for the tracking UI — the Activity only calls
 * renderState(TrackingUiState) and never holds any additional state fields.
 */
public class TrackingUiState {

    private final WalkState walkState;
    private final List<LatLng> mapPoints;
    private final double distanceKm;
    private final long elapsedSeconds;
    private final double paceMinPerKm;   // 0.0 until sufficient distance is accumulated
    private final String partnerName;
    private final boolean isCameraFollowingUser;

    public TrackingUiState(
            WalkState walkState,
            List<LatLng> mapPoints,
            double distanceKm,
            long elapsedSeconds,
            double paceMinPerKm,
            String partnerName,
            boolean isCameraFollowingUser) {
        this.walkState = walkState;
        this.mapPoints = mapPoints != null
                ? Collections.unmodifiableList(mapPoints)
                : Collections.emptyList();
        this.distanceKm = distanceKm;
        this.elapsedSeconds = elapsedSeconds;
        this.paceMinPerKm = paceMinPerKm;
        this.partnerName = partnerName;
        this.isCameraFollowingUser = isCameraFollowingUser;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public WalkState getWalkState()            { return walkState; }
    public List<LatLng> getMapPoints()         { return mapPoints; }
    public double getDistanceKm()              { return distanceKm; }
    public long getElapsedSeconds()            { return elapsedSeconds; }
    public double getPaceMinPerKm()            { return paceMinPerKm; }
    public String getPartnerName()             { return partnerName; }
    public boolean isCameraFollowingUser()     { return isCameraFollowingUser; }
}
