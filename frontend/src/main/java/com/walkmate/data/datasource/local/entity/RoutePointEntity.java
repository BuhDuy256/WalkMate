package com.walkmate.data.datasource.local.entity;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Room entity for the {@code route_points} table.
 *
 * Composite index on (sessionId, timestamp) supports the two most common
 * query patterns: "all points for session X ordered by time" and
 * "unsynced points for session X ordered by time".
 */
@Entity(
        tableName = "route_points",
        indices = {@Index(value = {"sessionId", "timestamp"})}
)
public class RoutePointEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    public String sessionId;
    public double lat;
    public double lng;
    public long timestamp;    // Unix epoch milliseconds from Location.getTime()
    public float accuracy;    // Horizontal accuracy radius in metres
    public boolean isSynced;  // Defaults to false; set to true after successful server sync
}
