package com.walkmate.data.datasource.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.walkmate.data.datasource.local.entity.GpsStreamEntity;

/**
 * Access to the per-{@code (sessionId, userId)} GPS stream cursor.
 *
 * <p>Every mutating call here is executed inside a caller-managed transaction
 * ({@code WalkMateDatabase.runInTransaction}); none of them is safe to call
 * standalone.</p>
 */
@Dao
public interface GpsStreamDao {

    @Query("SELECT * FROM gps_stream WHERE sessionId = :sessionId AND userId = :userId LIMIT 1")
    GpsStreamEntity find(String sessionId, String userId);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insertIfAbsent(GpsStreamEntity entity);

    /** Reserves the next sequence and returns it, then advances the counter. */
    @Query("SELECT nextSequence FROM gps_stream WHERE sessionId = :sessionId AND userId = :userId")
    Long currentNextSequence(String sessionId, String userId);

    @Query("UPDATE gps_stream SET nextSequence = nextSequence + 1, updatedAt = :now "
            + "WHERE sessionId = :sessionId AND userId = :userId")
    int bumpNextSequence(String sessionId, String userId, long now);

    @Query("UPDATE gps_stream SET streamId = :streamId, updatedAt = :now "
            + "WHERE sessionId = :sessionId AND userId = :userId")
    int setStreamId(String sessionId, String userId, String streamId, long now);

    /** Latest allocated sequence, or 0 when nothing has been accepted yet. */
    @Query("SELECT COALESCE(MAX(nextSequence) - 1, 0) FROM gps_stream "
            + "WHERE sessionId = :sessionId AND userId = :userId")
    long lastAllocatedSequence(String sessionId, String userId);

    @Query("UPDATE gps_stream SET finalSequence = :finalSequence, "
            + "recordedEndedAt = :recordedEndedAt, syncState = :syncState, updatedAt = :now "
            + "WHERE sessionId = :sessionId AND userId = :userId")
    int setSealed(String sessionId, String userId, long finalSequence,
                  Long recordedEndedAt, String syncState, long now);

    @Query("UPDATE gps_stream SET syncState = :syncState, updatedAt = :now "
            + "WHERE sessionId = :sessionId AND userId = :userId")
    int setSyncState(String sessionId, String userId, String syncState, long now);

    @Query("DELETE FROM gps_stream WHERE userId = :userId")
    void deleteByUserId(String userId);
}
