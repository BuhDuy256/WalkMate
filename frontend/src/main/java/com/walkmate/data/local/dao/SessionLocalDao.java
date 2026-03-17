package com.walkmate.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.walkmate.data.local.entity.SessionLocalEntity;

@Dao
public interface SessionLocalDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(SessionLocalEntity session);

    @Query("SELECT * FROM session_local WHERE sessionId = :sessionId LIMIT 1")
    SessionLocalEntity getById(String sessionId);

    @Query("UPDATE session_local SET totalDistanceMeters = :distance, totalDurationSeconds = :duration, hasPendingSync = :hasPendingSync, lastPointOrder = :lastOrder, updatedAt = :updatedAt WHERE sessionId = :sessionId")
    void updateStats(String sessionId, double distance, long duration, boolean hasPendingSync, int lastOrder,
            long updatedAt);

    @Query("UPDATE session_local SET uiState = :uiState, lastErrorMessage = :error, updatedAt = :updatedAt WHERE sessionId = :sessionId")
    void updateUiState(String sessionId, String uiState, String error, long updatedAt);
}
