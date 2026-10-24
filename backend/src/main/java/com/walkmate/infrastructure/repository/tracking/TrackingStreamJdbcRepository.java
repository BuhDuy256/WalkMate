package com.walkmate.infrastructure.repository.tracking;

import com.walkmate.domain.tracking.GpsStream;
import com.walkmate.domain.tracking.TrackingStreamConflictException;
import com.walkmate.domain.tracking.TrackingStreamRepository;
import com.walkmate.domain.tracking.TrackingStreamSyncState;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * JDBC implementation of {@link TrackingStreamRepository}.
 *
 * <p>All mutating methods assume the caller's write transaction is already
 * open — concurrency is serialized by the row-level lock held through
 * {@link #lockForUpdate} inside that same transaction.</p>
 */
@Repository
@RequiredArgsConstructor
public class TrackingStreamJdbcRepository implements TrackingStreamRepository {

    private final JdbcClient jdbcClient;

    private static final org.springframework.jdbc.core.RowMapper<GpsStream> FULL_MAP =
            (rs, rowNum) -> new GpsStream(
                    rs.getString("stream_id"),
                    rs.getString("session_id"),
                    rs.getString("user_id"),
                    getNullableLong(rs, "expected_final_sequence"),
                    rs.getTimestamp("recorded_ended_at") != null
                            ? rs.getTimestamp("recorded_ended_at").toInstant() : null,
                    rs.getTimestamp("upload_deadline_at") != null
                            ? rs.getTimestamp("upload_deadline_at").toInstant() : null,
                    TrackingStreamSyncState.valueOf(rs.getString("sync_state")),
                    rs.getInt("next_arrival_index"));

    @Override
    public boolean exists(String sessionId, String userId, String streamId) {
        Integer count = jdbcClient.sql("""
                        SELECT COUNT(*)
                          FROM tracking_streams
                         WHERE session_id = :sessionId
                           AND user_id    = :userId
                           AND stream_id  = :streamId
                        """)
                .param("sessionId", UUID.fromString(sessionId))
                .param("userId",    UUID.fromString(userId))
                .param("streamId",  UUID.fromString(streamId))
                .query(Integer.class)
                .single();
        return count != null && count > 0;
    }

    @Override
    public Optional<GpsStream> findBySessionAndUser(String sessionId, String userId) {
        return jdbcClient.sql("""
                        SELECT stream_id, session_id, user_id, expected_final_sequence,
                               recorded_ended_at, upload_deadline_at, sync_state,
                               next_arrival_index
                          FROM tracking_streams
                         WHERE session_id = :sessionId
                           AND user_id    = :userId
                        """)
                .param("sessionId", UUID.fromString(sessionId))
                .param("userId",    UUID.fromString(userId))
                .query(FULL_MAP)
                .optional();
    }

    @Override
    public Optional<GpsStream> findById(String streamId) {
        return jdbcClient.sql("""
                        SELECT stream_id, session_id, user_id, expected_final_sequence,
                               recorded_ended_at, upload_deadline_at, sync_state,
                               next_arrival_index
                          FROM tracking_streams
                         WHERE stream_id = :streamId
                        """)
                .param("streamId", UUID.fromString(streamId))
                .query(FULL_MAP)
                .optional();
    }

    @Override
    public GpsStream upsertStream(String sessionId, String userId, String streamId) {
        // Conflict target is deliberately omitted: an INSERT can collide with
        // the (session_id, user_id) unique key *or* with the primary key when a
        // different user presents an already-bound stream_id (partner replay).
        // Both are handled below as a stream conflict, never as a 500.
        jdbcClient.sql("""
                        INSERT INTO tracking_streams (stream_id, session_id, user_id)
                        VALUES (:streamId, :sessionId, :userId)
                        ON CONFLICT DO NOTHING
                        """)
                .param("streamId",  UUID.fromString(streamId))
                .param("sessionId", UUID.fromString(sessionId))
                .param("userId",    UUID.fromString(userId))
                .update();

        GpsStream stream = findBySessionAndUser(sessionId, userId).orElse(null);
        if (stream == null) {
            // The insert was rejected because the presented stream_id is
            // already bound to a different (session, user) pair.
            throw new TrackingStreamConflictException(
                    "Stream " + streamId + " is already bound to another user for session " + sessionId);
        }
        if (!stream.streamId().equals(streamId)) {
            throw new TrackingStreamConflictException(
                    "Stream conflict for session " + sessionId + " / user " + userId
                            + ": server stores " + stream.streamId()
                            + ", client presented " + streamId);
        }
        return stream;
    }

    @Override
    public int allocateArrivalIndex(String streamId, String syncRequestId) {
        // Idempotent replay: if this syncRequestId already committed a chunk row,
        // return that row's index instead of bumping the counter. A replayed
        // batch must not consume a new arrival index.
        Integer replayed = jdbcClient.sql("""
                        SELECT chunk_index
                          FROM session_point_chunks
                         WHERE sync_request_id = :syncRequestId
                        """)
                .param("syncRequestId", UUID.fromString(syncRequestId))
                .query(Integer.class)
                .optional()
                .orElse(null);
        if (replayed != null) {
            return replayed;
        }

        Integer bumped = jdbcClient.sql("""
                        UPDATE tracking_streams
                            SET next_arrival_index = next_arrival_index + 1,
                                updated_at         = now()
                          WHERE stream_id = :streamId
                         RETURNING next_arrival_index
                        """)
                .param("streamId", UUID.fromString(streamId))
                .query(Integer.class)
                .single();
        if (bumped == null || bumped == 0) {
            throw new IllegalStateException("Stream " + streamId + " not found");
        }
        return bumped - 1; // zero-based index of the row this allocation reserved
    }

    @Override
    public GpsStream lockForUpdate(String streamId) {
        return jdbcClient.sql("""
                        SELECT stream_id, session_id, user_id, expected_final_sequence,
                               recorded_ended_at, upload_deadline_at, sync_state,
                               next_arrival_index
                          FROM tracking_streams
                         WHERE stream_id = :streamId
                         FOR UPDATE
                        """)
                .param("streamId", UUID.fromString(streamId))
                .query(FULL_MAP)
                .single();
    }

    @Override
    public void sealStream(String streamId, long expectedFinalSequence, Instant recordedEndedAt,
                           Duration lateUploadWindow) {
        // Idempotent seal: re-sealing raises expected_final_sequence (never
        // lowers it) and refreshes the deadline from now; terminal states are
        // left untouched.
        int updated = jdbcClient.sql("""
                        UPDATE tracking_streams
                            SET expected_final_sequence = GREATEST(
                                    COALESCE(expected_final_sequence, 0), :expected),
                                recorded_ended_at   = COALESCE(:recordedEndedAt, recorded_ended_at),
                                upload_deadline_at  = now() + CAST(:deadlineSecs AS bigint) * INTERVAL '1 second',
                                sync_state          = CASE
                                      WHEN sync_state IN ('OPEN', 'SEALED') THEN 'SEALED'
                                      ELSE sync_state
                                  END,
                                updated_at          = now()
                          WHERE stream_id = :streamId
                        """)
                .param("streamId",  UUID.fromString(streamId))
                .param("expected",  expectedFinalSequence)
                .param("recordedEndedAt",
                        recordedEndedAt != null ? java.sql.Timestamp.from(recordedEndedAt) : null)
                .param("deadlineSecs", lateUploadWindow.toSeconds())
                .update();
        if (updated == 0) {
            throw new IllegalStateException("Stream " + streamId + " not found");
        }
    }

    @Override
    public TrackingStreamSyncState evaluateCompletion(String streamId) {
        GpsStream stream = lockForUpdate(streamId);

        if (stream.expectedFinalSequence() == null) {
            return stream.syncState(); // not sealed yet
        }

        if (stream.syncState() == TrackingStreamSyncState.COMPLETE
                || stream.syncState() == TrackingStreamSyncState.EXPIRED) {
            return stream.syncState(); // terminal
        }

        long finalSeq = stream.expectedFinalSequence();
        if (finalSeq == 0L) {
            // Client declared "no GPS points collected" — an empty route counts
            // as fully synced.
            transition(streamId, TrackingStreamSyncState.COMPLETE);
            return TrackingStreamSyncState.COMPLETE;
        }

        // Coverage: v2 rows have no overlaps by DB construction (GiST EXCLUDE),
        // so full coverage is: contiguous ranges start at 1 and reach finalSeq.
        Boolean covered = jdbcClient.sql("""
                        SELECT CASE
                                   WHEN COUNT(*) = 0
                                        OR MIN(c.seq_start) > 1
                                        OR MAX(c.seq_end) < :finalSeq
                                       THEN FALSE
                                   WHEN EXISTS (
                                          SELECT 1
                                            FROM (SELECT seq_start,
                                                         LAG(seq_end) OVER (ORDER BY seq_start)
                                                         AS prev_end
                                                    FROM session_point_chunks
                                                   WHERE stream_id = :streamId) x
                                           WHERE x.prev_end IS NOT NULL
                                             AND x.seq_start > x.prev_end + 1
                                        ) THEN FALSE
                                   ELSE TRUE
                               END
                          FROM session_point_chunks c
                         WHERE c.stream_id = :streamId
                        """)
                .param("finalSeq", finalSeq)
                .param("streamId", UUID.fromString(streamId))
                .query(Boolean.class)
                .single();
        if (Boolean.TRUE.equals(covered)) {
            transition(streamId, TrackingStreamSyncState.COMPLETE);
            return TrackingStreamSyncState.COMPLETE;
        }

        if (stream.uploadDeadlineAt() != null && stream.uploadDeadlineAt().isBefore(Instant.now())) {
            transition(streamId, TrackingStreamSyncState.EXPIRED);
            return TrackingStreamSyncState.EXPIRED;
        }
        return stream.syncState();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void transition(String streamId, TrackingStreamSyncState to) {
        jdbcClient.sql("""
                        UPDATE tracking_streams
                            SET sync_state = :state,
                                updated_at = now()
                          WHERE stream_id = :streamId
                            AND sync_state NOT IN ('COMPLETE', 'EXPIRED')
                        """)
                .param("streamId", UUID.fromString(streamId))
                .param("state", to.name())
                .update();
    }

    private static Long getNullableLong(ResultSet rs, String column) throws SQLException {
        long v = rs.getLong(column);
        return rs.wasNull() ? null : v;
    }
}
