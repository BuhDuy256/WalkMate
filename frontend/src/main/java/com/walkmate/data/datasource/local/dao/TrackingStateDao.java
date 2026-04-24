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
 * at runtime. Use {@link com.walkmate.data.repository.TrackingStateRepositoryImpl}
 * which runs operations on its own executor.
 *
 * Every query is scoped to {@code (sessionId, userId)} so that two users who
 * share a device and participate in the same session each maintain independent
 * timer state.
 */
@Dao
public interface TrackingStateDao {

    /** Inserts or replaces the state row for the (session, user) pair. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(TrackingStateEntity entity);

    /** Returns the state row for the (session, user) pair, or {@code null} if none exists. */
    @Query("SELECT * FROM tracking_state WHERE sessionId = :sessionId AND userId = :userId LIMIT 1")
    TrackingStateEntity getBySessionAndUser(String sessionId, String userId);

    /** Removes the state row for the (session, user) pair. */
    @Query("DELETE FROM tracking_state WHERE sessionId = :sessionId AND userId = :userId")
    void deleteBySessionAndUser(String sessionId, String userId);

    /**
     * Deletes all tracking state rows belonging to a specific user. Call this
     * on logout to prevent stale timer data from being restored when the next
     * user logs in on the same device.
     */
    @Query("DELETE FROM tracking_state WHERE userId = :userId")
    void deleteByUserId(String userId);
}
