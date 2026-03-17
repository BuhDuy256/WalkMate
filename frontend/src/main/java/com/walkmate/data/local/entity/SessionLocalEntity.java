package com.walkmate.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "session_local")
public class SessionLocalEntity {
    @PrimaryKey
    @NonNull
    public String sessionId;

    @NonNull
    public String backendState;

    @NonNull
    public String uiState;

    public double totalDistanceMeters;
    public long totalDurationSeconds;
    public int lastPointOrder;

    public boolean hasPendingSync;
    public String lastErrorMessage;
    public long updatedAt;
}
