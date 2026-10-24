package com.walkmate.domain.tracking;

import java.util.List;
import java.util.Optional;

public interface TrackingChunkRepository {

    /** Lightweight projection returned by {@link #findChunksAfterIndex}. */
    record ChunkRow(int chunkIndex, String polyline, long createdAtMs) {}

    /**
     * v2 projection with the route-sequence range, used by the out-of-order
     * read paths (history ordered by {@code seq_start}, live partner overlay
     * carrying per-chunk range information).
     */
    record ChunkRowV2(int chunkIndex, String polyline, long seqStart, long seqEnd, long createdAtMs) {}

    /**
     * Returns the next available chunk index for a specific user within a session.
     * Returns 0 when the user has no chunks yet; MAX(chunk_index) + 1 otherwise.
     *
     * <p><b>Legacy path only.</b> The v2 write path allocates the arrival index
     * from the stream row ({@link TrackingStreamRepository#allocateArrivalIndex})
     * so the counter is serialized with the stream write transaction.</p>
     */
    int nextChunkIndex(String sessionId, String userId);

    /**
     * Returns all encoded polyline strings for a session, ordered by chunk_index.
     * Returns chunks from all participants — used by the History flow to render both paths.
     */
    List<String> findPolylinesBySessionId(String sessionId);

    /**
     * Returns encoded polyline strings for a single participant, ordered by chunk_index ASC.
     *
     * <p>Read-path note: during the v1/v2 rollout this method returns <em>all</em>
     * of a participant's chunks (legacy + v2) in arrival order; history rendering
     * for v2 streams is switched to {@link #findV2PolylinesBySessionAndUser} in the
     * route-replay PR.</p>
     */
    List<String> findPolylinesBySessionAndUser(String sessionId, String userId);

    /**
     * v2 read path: the participant's route polylines ordered by
     * {@code seq_start ASC} (route order, not arrival order) across all of
     * the participant's streams.
     */
    List<String> findV2PolylinesBySessionAndUser(String sessionId, String userId);

    /**
     * Returns the number of chunks a specific user has uploaded for a session.
     */
    int countChunks(String sessionId, String userId);

    /**
     * Returns chunks for a single participant with {@code chunk_index > afterChunkIndex},
     * ordered ascending. Pass {@code -1} to fetch all chunks.
     * Used by the live partner-path overlay during an active session.
     */
    List<ChunkRow> findChunksAfterIndex(String sessionId, String userId, int afterChunkIndex);

    /**
     * v2 live poll: chunks with arrival {@code chunk_index > afterChunkIndex}
     * for a single participant, each carrying its route-sequence range so the
     * client can reorder out-of-order batches at render time.
     */
    List<ChunkRowV2> findV2ChunksAfterIndex(String sessionId, String userId, int afterChunkIndex);

    /**
     * Persists a single compressed chunk of GPS route data, tagged to the uploading user.
     *
     * @param sessionId      the owning walk session
     * @param userId         the user uploading this chunk
     * @param chunkIndex     monotonically increasing index scoped per (session, user)
     * @param polyline       Google Encoded Polyline string for the batch of coordinates
     * @param timestamps     packed big-endian longs (8 bytes × pointCount) of epoch-ms timestamps
     * @param pointCount     number of GPS points in this chunk
     * @param syncRequestId  R2: client-generated UUID for this push attempt; stored in a UNIQUE
     *                       column so the DB rejects duplicate inserts at constraint level
     */
    void saveChunk(String sessionId, String userId, int chunkIndex, String polyline,
                   byte[] timestamps, int pointCount, String syncRequestId);

    /**
     * Immutable view of an already-committed v2 chunk row, returned by
     * {@link #findCommittedBatch} for the replay/ACK verification path.
     * Every field the verification compares is a database value — no
     * client-declared field is trusted on replay.
     */
    record CommittedBatchRow(
            String streamId,
            String sessionId,
            String userId,
            long   seqStart,
            long   seqEnd,
            String payloadHash,
            int    pointCount
    ) {}

    /**
     * v2 replay gate: true when a v2 chunk row carrying this idempotency
     * key (batch/sync request id) is already committed.
     */
    boolean existsV2BatchBySyncRequestId(String syncRequestId);

    /**
     * Locate the committed v2 chunk by its idempotency key for replay
     * verification. Empty when the key was never committed.
     */
    Optional<CommittedBatchRow> findCommittedBatch(String syncRequestId);

    /**
     * Inserts a v2 chunk row — all-or-nothing, no constraint exception is
     * swallowed here: the SQL uses {@code ON CONFLICT (sync_request_id) DO
     * NOTHING RETURNING chunk_id} so a replayed batch_id is a no-op (the
     * caller detects "already committed" via {@link #findBySyncRequestId}),
     * while any <em>other</em> constraint violation (overlap via the GiST
     * EXCLUDE, unique per-user chunk_index, FK, check) aborts the caller's
     * whole transaction — the v2 contract never ACKs an uncommitted batch.
     *
     * @return true when a new row was inserted, false when the batch was
     *         already committed (idempotent no-op)
     */
    boolean trySaveV2Chunk(String sessionId, String userId, String streamId, int chunkIndex,
                           String polyline, byte[] timestamps, int pointCount,
                           long seqStart, long seqEnd, String payloadHashHex,
                           String syncRequestId);
}
