package com.walkmate.data.datasource.local.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Ánh xạ của RoutePoint dưới Local Database (SQLite).
 * Entity này có thêm cờ 'isSynced' để phục vụ chiến lược Batching.
 */
@Entity(tableName = "route_points")
public class RoutePointEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;

    public double lat;
    public double lng;
    public long timestamp;
    public float accuracy;

    // Cờ đánh dấu điểm này đã được đồng bộ với Server hay chưa
    public boolean isSynced = false;

    public RoutePointEntity(double lat, double lng, long timestamp, float accuracy) {
        this.lat = lat;
        this.lng = lng;
        this.timestamp = timestamp;
        this.accuracy = accuracy;
    }
}
