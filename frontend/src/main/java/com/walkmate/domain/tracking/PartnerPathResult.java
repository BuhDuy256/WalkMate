package com.walkmate.domain.tracking;

import com.google.android.gms.maps.model.LatLng;

import java.util.Collections;
import java.util.List;

/**
 * Domain result returned by {@link TrackingRepository#fetchPartnerPath}.
 *
 * Contains only the <em>new</em> decoded LatLng points since the last successful
 * fetch (based on {@code afterChunkIndex}). The ViewModel accumulates these into
 * its own in-memory list for rendering.
 */
public class PartnerPathResult {

    private final List<LatLng> newPoints;
    private final int          lastChunkIndex;
    private final long         lastChunkCreatedAtMs;
    private final String       partnerStatus;

    public PartnerPathResult(List<LatLng> newPoints, int lastChunkIndex,
                             long lastChunkCreatedAtMs, String partnerStatus) {
        this.newPoints            = newPoints != null
                ? Collections.unmodifiableList(newPoints) : Collections.emptyList();
        this.lastChunkIndex       = lastChunkIndex;
        this.lastChunkCreatedAtMs = lastChunkCreatedAtMs;
        this.partnerStatus        = partnerStatus != null ? partnerStatus : "PENDING";
    }

    public List<LatLng> getNewPoints()        { return newPoints; }
    public int getLastChunkIndex()            { return lastChunkIndex; }
    public long getLastChunkCreatedAtMs()     { return lastChunkCreatedAtMs; }
    public String getPartnerStatus()          { return partnerStatus; }
}
