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
 *
 * <p>v2 columns (nullable so pre-existing rows survive the 4 → 5 migration):</p>
 * <ul>
 *   <li>{@code streamId} — the upload stream this point belongs to.</li>
 *   <li>{@code sequence} — device-assigned source order, 1-based per stream.</li>
 *   <li>{@code batchId} — set once the point is sealed; frozen afterwards.</li>
 * </ul>
 * <p>SQLite allows multiple NULLs in a UNIQUE index, so the legacy rows
 * (streamId/sequence NULL) never collide with each other.</p>
 */
@Entity(
        tableName = "route_points",
        indices = {
                @Index(value = {"sessionId", "userId", "timestamp"}),
                @Index(name = "index_route_points_stream_sequence",
                        value = {"sessionId", "userId", "streamId", "sequence"},
                        unique = true),
                @Index(name = "index_route_points_batchId", value = {"batchId"})
        }
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
    public boolean isSynced;  // Defaults to false; set to true after a verified batch ACK

    // ── v2 stream identity (nullable for legacy rows) ─────────────────────────
    public String streamId;
    public Long sequence;
    public String batchId;
}
