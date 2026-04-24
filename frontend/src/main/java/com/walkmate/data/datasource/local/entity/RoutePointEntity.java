package com.walkmate.data.datasource.local.entity;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Room entity for the {@code route_points} table.
 *
 * Composite index on (sessionId, userId, timestamp) scopes every query to the
 * specific user who recorded the point. This prevents cross-account data leaks
 * when two users share a device and participate in the same session.
 */
@Entity(
        tableName = "route_points",
        indices = {@Index(value = {"sessionId", "userId", "timestamp"})}
)
public class RoutePointEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    public String sessionId;
    public String userId;     // The logged-in user who recorded this GPS fix
    public double lat;
    public double lng;
    public long timestamp;    // Unix epoch milliseconds from Location.getTime()
    public float accuracy;    // Horizontal accuracy radius in metres
    public boolean isSynced;  // Defaults to false; set to true after successful server sync
}
