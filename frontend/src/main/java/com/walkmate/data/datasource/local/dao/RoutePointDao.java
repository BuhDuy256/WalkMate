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
     * Synchronous read used by the v1 batch-sync logic (called on executor thread).
     *
     * @deprecated v2 seals immutable batches instead of pushing whatever happens to
     *     be unsynced; use {@link #getUnbatchedPoints(String, String, int)} so a
     *     retry cannot silently change the payload of a batch id the server already
     *     saw.
     */
    @Deprecated
    @Query("SELECT * FROM route_points WHERE sessionId = :sessionId AND userId = :userId AND isSynced = 0 ORDER BY timestamp ASC")
    List<RoutePointEntity> getUnsyncedPoints(String sessionId, String userId);

    /**
     * Points that have not been sealed into any batch yet, in source order.
     * This is the input to batch sealing; once a point has a {@code batchId} it
     * never appears here again.
     *
     * <p>{@code sequence IS NOT NULL} is a deliberate quarantine: rows written
     * before the v1→v2 upgrade have no sequence identity, and assigning them one
     * now would claim the v2 dedupe guarantee for data that may already have been
     * half-uploaded under the old contract. They are left untouched and counted
     * separately by {@link #countUnsequencedLegacy}.</p>
     */
    @Query("SELECT * FROM route_points "
            + "WHERE sessionId = :sessionId AND userId = :userId "
            + "  AND batchId IS NULL AND sequence IS NOT NULL "
            + "ORDER BY sequence ASC, id ASC LIMIT :limit")
    List<RoutePointEntity> getUnbatchedPoints(String sessionId, String userId, int limit);

    @Query("SELECT COUNT(*) FROM route_points "
            + "WHERE sessionId = :sessionId AND userId = :userId "
            + "  AND batchId IS NULL AND sequence IS NOT NULL")
    int countUnbatched(String sessionId, String userId);

    /**
     * Pre-upgrade rows that carry no sequence. They are intentionally never
     * sealed into a v2 batch; surfacing the count lets the UI/diagnostics report
     * "this session still holds legacy points" instead of silently ignoring them.
     */
    @Query("SELECT COUNT(*) FROM route_points "
            + "WHERE sessionId = :sessionId AND userId = :userId AND sequence IS NULL")
    int countUnsequencedLegacy(String sessionId, String userId);

    /** Frozen payload of a sealed batch — membership cannot change after sealing. */
    @Query("SELECT * FROM route_points WHERE batchId = :batchId ORDER BY sequence ASC")
    List<RoutePointEntity> getPointsOfBatch(String batchId);

    /** Stamps the frozen membership onto the sealed points (membership immutability). */
    @Query("UPDATE route_points SET batchId = :batchId WHERE id IN (:ids)")
    void assignBatch(List<Long> ids, String batchId);

    /** Marks only the points that the server ACKed — never an unverified id list. */
    @Query("UPDATE route_points SET isSynced = 1 WHERE batchId = :batchId")
    void markBatchSynced(String batchId);

    /** Optional cleanup once a batch is ACKed and retention has elapsed. */
    @Query("DELETE FROM route_points WHERE batchId IN "
            + "(SELECT batchId FROM gps_batches WHERE state = 'ACKED')")
    void deleteRowsOfAckedBatches();

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
