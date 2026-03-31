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
     * every time a new row is inserted for the given session.
     */
    @Query("SELECT * FROM route_points WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    LiveData<List<RoutePointEntity>> getPointsBySessionId(String sessionId);

    /**
     * Synchronous read used by the batch-sync logic (called on executor thread).
     */
    @Query("SELECT * FROM route_points WHERE sessionId = :sessionId AND isSynced = 0 ORDER BY timestamp ASC")
    List<RoutePointEntity> getUnsyncedPoints(String sessionId);

    /**
     * Returns the count of unsynced points for a session.
     * Used to decide whether to trigger a batch push.
     */
    @Query("SELECT COUNT(id) FROM route_points WHERE sessionId = :sessionId AND isSynced = 0")
    int getUnsyncedCount(String sessionId);

    /**
     * Bulk-marks a list of point IDs as synced after a successful backend push.
     */
    @Query("UPDATE route_points SET isSynced = 1 WHERE id IN (:ids)")
    void markAsSynced(List<Long> ids);
}
