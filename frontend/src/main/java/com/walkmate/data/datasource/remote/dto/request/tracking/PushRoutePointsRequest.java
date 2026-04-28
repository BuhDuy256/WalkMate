package com.walkmate.data.datasource.remote.dto.request.tracking;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Request body for POST /api/v1/tracking/sync.
 *
 * Sends a batch of GPS points collected during a walk session to the backend
 * for persistent storage and potential partner-location sharing.
 */
public class PushRoutePointsRequest {

    /** R2: Unique ID for this push attempt. Backend uses it as a deduplication key. */
    @SerializedName("sync_request_id")
    private final String syncRequestId;

    @SerializedName("session_id")
    private final String sessionId;

    @SerializedName("points")
    private final List<RoutePointPayload> points;

    public PushRoutePointsRequest(String syncRequestId, String sessionId, List<RoutePointPayload> points) {
        this.syncRequestId = syncRequestId;
        this.sessionId     = sessionId;
        this.points        = points;
    }

    public String getSyncRequestId()           { return syncRequestId; }
    public String getSessionId()               { return sessionId; }
    public List<RoutePointPayload> getPoints() { return points; }

    // ── Nested payload ────────────────────────────────────────────────────────

    public static class RoutePointPayload {

        @SerializedName("local_id")
        private final long localId;     // local Room row ID — backend echoes it back for markAsSynced

        @SerializedName("lat")
        private final double lat;

        @SerializedName("lng")
        private final double lng;

        @SerializedName("timestamp")
        private final long timestamp;

        @SerializedName("accuracy")
        private final float accuracy;

        public RoutePointPayload(long localId, double lat, double lng, long timestamp, float accuracy) {
            this.localId = localId;
            this.lat = lat;
            this.lng = lng;
            this.timestamp = timestamp;
            this.accuracy = accuracy;
        }

        public long getLocalId()    { return localId; }
        public double getLat()      { return lat; }
        public double getLng()      { return lng; }
        public long getTimestamp()  { return timestamp; }
        public float getAccuracy()  { return accuracy; }
    }
}
