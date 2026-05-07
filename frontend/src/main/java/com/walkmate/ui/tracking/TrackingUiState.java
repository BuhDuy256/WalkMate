package com.walkmate.ui.tracking;

import com.google.android.gms.maps.model.LatLng;
import com.walkmate.domain.tracking.PartnerOverlayState;
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
    private final double paceMinPerKm;
    private final String partnerName;
    private final boolean isCameraFollowingUser;
    /** Seconds remaining before Complete Walk is allowed; 0 when complete is permitted. */
    private final long completeTooEarlySeconds;
    /** True while a complete API call is in flight (FINISHING state). */
    private final boolean isSaving;

    // ── Partner path overlay ──────────────────────────────────────────────────

    /** Full accumulated partner path decoded from server polylines. Empty until partner sends GPS. */
    private final List<LatLng> partnerMapPoints;
    /** Current rendering state of the partner overlay — drives the status label. */
    private final PartnerOverlayState partnerOverlayState;
    /** Seconds since the last received partner GPS chunk; 0 when no chunk has ever arrived. */
    private final long partnerLastUpdatedSeconds;
    /**
     * Non-null when a partner status transition just occurred and the Activity
     * should show a one-time Toast. Null after {@link TrackingViewModel#consumePartnerNotice()}.
     */
    private final String partnerNoticeMessage;

    public TrackingUiState(
            WalkState walkState,
            List<LatLng> mapPoints,
            double distanceKm,
            long elapsedSeconds,
            double paceMinPerKm,
            String partnerName,
            boolean isCameraFollowingUser,
            long completeTooEarlySeconds,
            boolean isSaving,
            List<LatLng> partnerMapPoints,
            PartnerOverlayState partnerOverlayState,
            long partnerLastUpdatedSeconds,
            String partnerNoticeMessage) {
        this.walkState = walkState;
        this.mapPoints = mapPoints != null
                ? Collections.unmodifiableList(mapPoints)
                : Collections.emptyList();
        this.distanceKm = distanceKm;
        this.elapsedSeconds = elapsedSeconds;
        this.paceMinPerKm = paceMinPerKm;
        this.partnerName = partnerName;
        this.isCameraFollowingUser = isCameraFollowingUser;
        this.completeTooEarlySeconds = completeTooEarlySeconds;
        this.isSaving = isSaving;
        this.partnerMapPoints = partnerMapPoints != null
                ? Collections.unmodifiableList(partnerMapPoints)
                : Collections.emptyList();
        this.partnerOverlayState = partnerOverlayState != null
                ? partnerOverlayState : PartnerOverlayState.WAITING_FOR_PARTNER;
        this.partnerLastUpdatedSeconds = partnerLastUpdatedSeconds;
        this.partnerNoticeMessage = partnerNoticeMessage;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public WalkState getWalkState()                         { return walkState; }
    public List<LatLng> getMapPoints()                      { return mapPoints; }
    public double getDistanceKm()                           { return distanceKm; }
    public long getElapsedSeconds()                         { return elapsedSeconds; }
    public double getPaceMinPerKm()                         { return paceMinPerKm; }
    public String getPartnerName()                          { return partnerName; }
    public boolean isCameraFollowingUser()                  { return isCameraFollowingUser; }
    public long getCompleteTooEarlySeconds()                { return completeTooEarlySeconds; }
    public boolean isSaving()                               { return isSaving; }
    public List<LatLng> getPartnerMapPoints()               { return partnerMapPoints; }
    public PartnerOverlayState getPartnerOverlayState()     { return partnerOverlayState; }
    public long getPartnerLastUpdatedSeconds()              { return partnerLastUpdatedSeconds; }
    public String getPartnerNoticeMessage()                 { return partnerNoticeMessage; }
}
