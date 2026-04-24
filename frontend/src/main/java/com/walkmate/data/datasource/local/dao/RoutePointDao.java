package com.walkmate.data.datasource.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.walkmate.data.datasource.local.entity.RoutePointEntity;

import java.util.List;

/**
 * Data Access Object for the {@code route_points} table.
 *
 * All methods that touch the DB (insert / query) must be called from a
 * background thread — Room enforces this at runtime when called on the
 * main thread. The LiveData overload is the sole exception; Room delivers
 * it on the main thread automatically.
 *
 * Every query is scoped to {@code (sessionId, userId)} to prevent GPS path
 * data from leaking across accounts on a shared device.
 */
@Dao
public interface RoutePointDao {

    /**
     * Inserts a single GPS fix and returns the auto-generated row ID.
     */
    @Insert
    long insertPoint(RoutePointEntity entity);

    /**
     * Reactive read — Room re-emits the full list on the main thread
     * every time a new row is inserted for the given session and user.
     */
    @Query("SELECT * FROM route_points WHERE sessionId = :sessionId AND userId = :userId ORDER BY timestamp ASC")
    LiveData<List<RoutePointEntity>> getPointsBySessionAndUser(String sessionId, String userId);

    /**
     * Synchronous read used by the batch-sync logic (called on executor thread).
     */
    @Query("SELECT * FROM route_points WHERE sessionId = :sessionId AND userId = :userId AND isSynced = 0 ORDER BY timestamp ASC")
    List<RoutePointEntity> getUnsyncedPoints(String sessionId, String userId);

    /**
     * Returns the count of unsynced points for a session and user.
     */
    @Query("SELECT COUNT(id) FROM route_points WHERE sessionId = :sessionId AND userId = :userId AND isSynced = 0")
    int getUnsyncedCount(String sessionId, String userId);

    /**
     * Bulk-marks a list of point IDs as synced after a successful backend push.
     */
    @Query("UPDATE route_points SET isSynced = 1 WHERE id IN (:ids)")
    void markAsSynced(List<Long> ids);

    /**
     * Deletes all GPS points recorded by a specific user. Call this on logout
     * to prevent the previous user's path data from being visible to the next
     * user who signs in on the same device.
     */
    @Query("DELETE FROM route_points WHERE userId = :userId")
    void deleteByUserId(String userId);
}
