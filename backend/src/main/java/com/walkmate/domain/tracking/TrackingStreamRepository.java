package com.walkmate.domain.tracking;

import com.walkmate.domain.tracking.TrackingStreamSyncState;

import java.time.Instant;
import java.util.Optional;

/**
 * Persistence port for GPS v2 streams.
 *
 * <p>All mutating methods must run inside the caller's write transaction
 * (the same transaction that inserts the chunk row): the stream row is the
 * lock target for every v2 batch write, and the deadline/coverage state
 * transitions must be atomic with the inserts they gate.</p>
 *
 * <p>Legacy behaviour is preserved: nothing here touches rows with
 * {@code stream_id IS NULL}.</p>
 */
public interface TrackingStreamRepository {

    /** True when the stream for exactly (session, user) with this id exists. */
    boolean exists(String sessionId, String userId, String streamId);

    /**
     * Snapshot of the stream for (session, user), any id.
     */
    Optional<GpsStream> findBySessionAndUser(String sessionId, String userId);

    /** Snapshot by stream id alone. Used by the seal endpoint. */
    Optional<GpsStream> findById(String streamId);

    /**
     * Upserts the stream row and returns the current snapshot.
     *
     * <p>Idempotent: a second call with the same (session, user, streamId)
     * is a no-op. A conflicting stream id for an existing stream is the only
     * way a caller can detect a multi-device conflict via
     * {@link TrackingStreamConflictException} — the caller is expected to
     * use {@link #exists} first in the command flow.</p>
     */
    GpsStream upsertStream(String sessionId, String userId, String streamId);

    /**
     * Allocates the next arrival cursor for the stream inside the current
     * transaction.
     *
     * <p>Idempotent by design: if {@code syncRequestId} was already stored on
     * a chunk row, the <em>same</em> index is returned again instead of
     * bumping the counter — a replayed batch must not consume a new index.
     * Must be called after the row-level lock is held (see
     * {@link #lockForUpdate}).</p>
     */
    int allocateArrivalIndex(String streamId, String syncRequestId);

    /**
     * Row-level lock on the stream (SELECT ... FOR UPDATE). Callers hold the
     * lock from this call until the surrounding transaction commits or rolls
     * back — this serializes concurrent batch writes for the same stream.
     *
     * @return the stream snapshot under the lock
     */
    GpsStream lockForUpdate(String streamId);

    /**
     * Records the client's final sequence for the stream and moves
     * OPEN → SEALED with an upload deadline of
     * {@code now + deadlineDays}. Idempotent: re-sealing with lower or equal
     * final sequence is a no-op; a higher value extends the range.
     *
     * <p>Honors the late-upload policy: a stream may be sealed after the
     * session's personal status is COMPLETED — the deadline is counted from
     * the seal moment, not the completion moment.</p>
     */
    void sealStream(String streamId, long expectedFinalSequence, Instant recordedEndedAt,
                    java.time.Duration lateUploadWindow);

    /**
     * Evaluates the terminal state for a stream: if coverage is complete the
     * stream moves to COMPLETE; if the deadline has passed it moves to
     * EXPIRED. Must run inside the same transaction as the coverage check.
     *
     * @return the sync state after evaluation
     */
    TrackingStreamSyncState evaluateCompletion(String streamId);
}
