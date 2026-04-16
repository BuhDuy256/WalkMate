package com.walkmate.data.datasource.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.walkmate.data.datasource.local.entity.TrackingStateEntity;

/**
 * Data Access Object for the {@code tracking_state} table.
 *
 * All methods must be called from a background thread — Room enforces this
 * at runtime. Use {@link TrackingStateRepositoryImpl} which runs operations
 * on its own executor.
 */
@Dao
public interface TrackingStateDao {

    /** Inserts or replaces the state row for the session. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(TrackingStateEntity entity);

    /** Returns the state row for the session, or {@code null} if none exists. */
    @Query("SELECT * FROM tracking_state WHERE sessionId = :sessionId LIMIT 1")
    TrackingStateEntity getBySessionId(String sessionId);

    /** Removes the state row for the session. */
    @Query("DELETE FROM tracking_state WHERE sessionId = :sessionId")
    void deleteBySessionId(String sessionId);
}
