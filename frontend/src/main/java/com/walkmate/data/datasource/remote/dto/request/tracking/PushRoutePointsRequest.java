package com.walkmate.data.datasource.remote.dto.request.tracking;

import java.util.List;

/**
 * Request body for POST /api/v1/tracking/sync.
 *
 * Sends a batch of GPS points collected during a walk session to the backend
 * for persistent storage and potential partner-location sharing.
 */
public class PushRoutePointsRequest {

    private final String sessionId;
    private final List<RoutePointPayload> points;

    public PushRoutePointsRequest(String sessionId, List<RoutePointPayload> points) {
        this.sessionId = sessionId;
        this.points = points;
    }

    public String getSessionId() { return sessionId; }
    public List<RoutePointPayload> getPoints() { return points; }

    // ── Nested payload ────────────────────────────────────────────────────────

    public static class RoutePointPayload {
        private final long localId;     // local Room row ID — backend echoes it back for markAsSynced
        private final double lat;
        private final double lng;
        private final long timestamp;
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
