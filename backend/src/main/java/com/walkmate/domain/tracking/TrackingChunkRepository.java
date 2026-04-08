package com.walkmate.domain.tracking;

import java.util.List;

public interface TrackingChunkRepository {

    /**
     * Returns the next available chunk index for a specific user within a session.
     * Returns 0 when the user has no chunks yet; MAX(chunk_index) + 1 otherwise.
     */
    int nextChunkIndex(String sessionId, String userId);

    /**
     * Returns all encoded polyline strings for a session, ordered by chunk_index.
     * Returns chunks from all participants — used by the History flow to render both paths.
     */
    List<String> findPolylinesBySessionId(String sessionId);

    /**
     * Returns encoded polyline strings for a single participant, ordered by chunk_index ASC.
     */
    List<String> findPolylinesBySessionAndUser(String sessionId, String userId);

    /**
     * Returns the number of chunks a specific user has uploaded for a session.
     */
    int countChunks(String sessionId, String userId);

    /**
     * Persists a single compressed chunk of GPS route data, tagged to the uploading user.
     *
     * @param sessionId   the owning walk session
     * @param userId      the user uploading this chunk
     * @param chunkIndex  monotonically increasing index scoped per (session, user)
     * @param polyline    Google Encoded Polyline string for the batch of coordinates
     * @param timestamps  packed big-endian longs (8 bytes × pointCount) of epoch-ms timestamps
     * @param pointCount  number of GPS points in this chunk
     */
    void saveChunk(String sessionId, String userId, int chunkIndex, String polyline,
                   byte[] timestamps, int pointCount);
}
