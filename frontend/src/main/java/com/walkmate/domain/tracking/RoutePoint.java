package com.walkmate.domain.tracking;

/**
 * Lightweight domain model representing a single GPS fix belonging to a walk session.
 * This is the object the ViewModel and higher layers work with — no Room annotations,
 * no DTO fields, no Android imports.
 *
 * <p>v2 identity is {@code (streamId, sequence)}. {@code sequence} is assigned by the
 * local queue store inside the same transaction that inserts the row, so it is
 * gap-free per stream even across app restarts. {@code id} remains only a Room
 * surrogate key and is never used as an ordering key on the server.</p>
 *
 * <p>{@code batchId} is null until the point is sealed into a batch, after which it is
 * frozen — a sealed batch is never re-cut.</p>
 */
public class RoutePoint {

    private final long id;          // 0 for unsaved points; assigned by Room on insert
    private final String sessionId;
    private final String userId;    // owner; stamped at persistence time
    private final String streamId;  // v2 upload stream; null for legacy rows
    private final long sequence;    // 1-based per stream; 0 when not yet assigned
    private final String batchId;   // null until sealed
    private final double lat;
    private final double lng;
    private final long timestamp;   // Unix epoch milliseconds
    private final float accuracy;   // Horizontal accuracy in metres
    private final boolean isSynced;

    /** Legacy/unsaved constructor — no stream identity yet. */
    public RoutePoint(long id, String sessionId, double lat, double lng,
                      long timestamp, float accuracy, boolean isSynced) {
        this(id, sessionId, null, null, 0L, null, lat, lng, timestamp, accuracy, isSynced);
    }

    public RoutePoint(long id, String sessionId, String userId, String streamId,
                      long sequence, String batchId, double lat, double lng,
                      long timestamp, float accuracy, boolean isSynced) {
        this.id = id;
        this.sessionId = sessionId;
        this.userId = userId;
        this.streamId = streamId;
        this.sequence = sequence;
        this.batchId = batchId;
        this.lat = lat;
        this.lng = lng;
        this.timestamp = timestamp;
        this.accuracy = accuracy;
        this.isSynced = isSynced;
    }

    public long getId()          { return id; }
    public String getSessionId() { return sessionId; }
    public String getUserId()    { return userId; }
    public String getStreamId()  { return streamId; }
    public long getSequence()    { return sequence; }
    public String getBatchId()   { return batchId; }
    public double getLat()       { return lat; }
    public double getLng()       { return lng; }
    public long getTimestamp()   { return timestamp; }
    public float getAccuracy()   { return accuracy; }
    public boolean isSynced()    { return isSynced; }
}
