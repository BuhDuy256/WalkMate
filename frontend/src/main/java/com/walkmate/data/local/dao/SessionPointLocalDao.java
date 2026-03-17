package com.walkmate.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.walkmate.data.local.entity.SessionPointLocalEntity;

import java.util.List;

@Dao
public interface SessionPointLocalDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insert(SessionPointLocalEntity entity);

    @Query("SELECT * FROM session_point_local WHERE sessionId = :sessionId ORDER BY pointOrder ASC")
    LiveData<List<SessionPointLocalEntity>> observeAllBySession(String sessionId);

    @Query("SELECT * FROM session_point_local WHERE sessionId = :sessionId ORDER BY pointOrder DESC LIMIT 1")
    SessionPointLocalEntity getLastPoint(String sessionId);

    @Query("SELECT COALESCE(MAX(pointOrder), -1) FROM session_point_local WHERE sessionId = :sessionId")
    int getMaxOrder(String sessionId);

    @Query("SELECT * FROM session_point_local WHERE sessionId = :sessionId AND syncStatus = 'PENDING' ORDER BY pointOrder ASC LIMIT :limit")
    List<SessionPointLocalEntity> getPendingBatch(String sessionId, int limit);

    @Query("UPDATE session_point_local SET syncStatus = :syncStatus, batchToken = :batchToken, updatedAt = :updatedAt WHERE localId IN (:ids)")
    void markBatchStatus(List<Long> ids, String syncStatus, String batchToken, long updatedAt);

    @Query("UPDATE session_point_local SET syncStatus = 'SYNCED', batchToken = NULL, updatedAt = :updatedAt WHERE batchToken = :batchToken")
    void markSyncedByBatch(String batchToken, long updatedAt);

    @Query("UPDATE session_point_local SET syncStatus = 'PENDING', retryCount = retryCount + 1, batchToken = NULL, updatedAt = :updatedAt WHERE batchToken = :batchToken")
    void requeueBatch(String batchToken, long updatedAt);

    @Query("SELECT COUNT(*) FROM session_point_local WHERE sessionId = :sessionId AND syncStatus IN ('PENDING','SYNCING','FAILED')")
    int countUnsynced(String sessionId);
}
