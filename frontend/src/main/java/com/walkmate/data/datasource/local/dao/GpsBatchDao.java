package com.walkmate.data.datasource.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.walkmate.data.datasource.local.entity.GpsBatchEntity;

import java.util.List;

/**
 * Access to the persistent upload queue ({@code gps_batches}).
 *
 * <p>All mutating calls run inside a caller-managed Room transaction so the
 * batch state and its point rows move together — a batch is never marked
 * {@code ACKED} without its points being marked synced in the same commit.</p>
 */
@Dao
public interface GpsBatchDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(GpsBatchEntity entity);

    @Query("SELECT * FROM gps_batches WHERE batchId = :batchId LIMIT 1")
    GpsBatchEntity find(String batchId);

    /**
     * Batches eligible for an upload attempt right now.
     *
     * <p>Three conditions, all necessary:</p>
     * <ul>
     *   <li>{@code state = 'PENDING'} <b>and</b> the backoff window has elapsed —
     *       without the {@code nextAttemptAt} check a released batch is retried on
     *       the very next pass, so the exponential backoff would be computed,
     *       stored, and then ignored (hammering a failing server every trigger);</li>
     *   <li>{@code state = 'IN_FLIGHT'} whose lease expired — the worker that owned
     *       it died mid-upload;</li>
     *   <li>nothing BLOCKED or ACKED ever re-enters the queue.</li>
     * </ul>
     */
    @Query("SELECT * FROM gps_batches "
            + "WHERE sessionId = :sessionId AND userId = :userId "
            + "  AND ( (state = 'PENDING'   AND nextAttemptAt <= :now) "
            + "     OR (state = 'IN_FLIGHT' AND leaseUntil    <= :now) ) "
            + "ORDER BY seqStart ASC")
    List<GpsBatchEntity> findDrainable(String sessionId, String userId, long now);

    @Query("SELECT * FROM gps_batches "
            + "WHERE sessionId = :sessionId AND userId = :userId "
            + "  AND state IN ('PENDING', 'IN_FLIGHT') "
            + "ORDER BY seqStart ASC")
    List<GpsBatchEntity> findOutstanding(String sessionId, String userId);

    @Query("SELECT COUNT(*) FROM gps_batches "
            + "WHERE sessionId = :sessionId AND userId = :userId "
            + "  AND state IN ('PENDING', 'IN_FLIGHT')")
    int countOutstanding(String sessionId, String userId);

    /** Takes the lease. Returns 0 when another worker already holds it. */
    @Query("UPDATE gps_batches SET state = 'IN_FLIGHT', leaseUntil = :leaseUntil "
            + "WHERE batchId = :batchId "
            + "  AND ( (state = 'PENDING'   AND nextAttemptAt <= :now) "
            + "     OR (state = 'IN_FLIGHT' AND leaseUntil    <= :now) )")
    int claim(String batchId, long now, long leaseUntil);

    /**
     * Records the verified ACK. Only the batch whose id, range and hash match
     * the response is promoted to ACKED — an unverified ACK never marks points.
     */
    @Query("UPDATE gps_batches SET state = 'ACKED', ackedAt = :now, leaseUntil = 0, "
            + "blockReason = NULL "
            + "WHERE batchId = :batchId AND streamId = :streamId "
            + "  AND seqStart = :seqStart AND seqEnd = :seqEnd AND payloadHash = :payloadHash")
    int markAcked(String batchId, String streamId,
                  long seqStart, long seqEnd, String payloadHash, long now);

    @Query("UPDATE gps_batches SET state = 'PENDING', retryCount = retryCount + 1, "
            + "nextAttemptAt = :nextAttemptAt, leaseUntil = 0 "
            + "WHERE batchId = :batchId")
    void release(String batchId, long nextAttemptAt);

    @Query("UPDATE gps_batches SET state = 'BLOCKED', blockReason = :reason, "
            + "leaseUntil = 0 WHERE batchId = :batchId")
    void block(String batchId, String reason);

    @Query("SELECT COUNT(*) FROM gps_batches "
            + "WHERE sessionId = :sessionId AND userId = :userId AND state = 'BLOCKED'")
    int countBlocked(String sessionId, String userId);

    @Query("SELECT * FROM gps_batches "
            + "WHERE sessionId = :sessionId AND userId = :userId AND state = 'BLOCKED' "
            + "ORDER BY seqStart ASC")
    List<GpsBatchEntity> findBlocked(String sessionId, String userId);

    /**
     * Sessions that still have unsent data. Used on app start to re-arm the
     * uploader after a process kill — the queue is what survives the restart,
     * not the service that produced it.
     */
    @Query("SELECT DISTINCT sessionId FROM gps_batches "
            + "WHERE userId = :userId AND state IN ('PENDING', 'IN_FLIGHT')")
    List<String> findSessionsWithOutstanding(String userId);

    /** Highest sequence the server has ACKed, or 0 when nothing is acknowledged. */
    @Query("SELECT COALESCE(MAX(seqEnd), 0) FROM gps_batches "
            + "WHERE sessionId = :sessionId AND userId = :userId AND state = 'ACKED'")
    long highestAckedSeq(String sessionId, String userId);

    @Query("DELETE FROM gps_batches WHERE userId = :userId")
    void deleteByUserId(String userId);
}
