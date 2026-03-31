package com.walkmate.domain.tracking;

/**
 * Lightweight domain model representing a single GPS fix belonging to a walk session.
 * This is the object the ViewModel and higher layers work with — no Room annotations,
 * no DTO fields, no Android imports.
 */
public class RoutePoint {

    private final long id;          // 0 for unsaved points; assigned by Room on insert
    private final String sessionId;
    private final double lat;
    private final double lng;
    private final long timestamp;   // Unix epoch milliseconds
    private final float accuracy;   // Horizontal accuracy in metres
    private final boolean isSynced;

    public RoutePoint(
            long id,
            String sessionId,
            double lat,
            double lng,
            long timestamp,
            float accuracy,
            boolean isSynced) {
        this.id = id;
        this.sessionId = sessionId;
        this.lat = lat;
        this.lng = lng;
        this.timestamp = timestamp;
        this.accuracy = accuracy;
        this.isSynced = isSynced;
    }

    public long getId()          { return id; }
    public String getSessionId() { return sessionId; }
    public double getLat()       { return lat; }
    public double getLng()       { return lng; }
    public long getTimestamp()   { return timestamp; }
    public float getAccuracy()   { return accuracy; }
    public boolean isSynced()    { return isSynced; }
}
