package com.walkmate.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "session_point_local", indices = {
        @Index(value = { "sessionId", "syncStatus", "pointOrder" }),
        @Index(value = { "sessionId", "pointOrder" }, unique = true)
})
public class SessionPointLocalEntity {
    @PrimaryKey(autoGenerate = true)
    public long localId;

    @NonNull
    public String sessionId;

    public int pointOrder;
    public double lat;
    public double lng;
    public long time;

    @NonNull
    public String syncStatus;

    public int retryCount;
    public String batchToken;
    public long createdAt;
    public long updatedAt;
}
