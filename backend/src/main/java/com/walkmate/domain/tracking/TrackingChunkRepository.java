package com.walkmate.domain.tracking;

import java.util.List;

public interface TrackingChunkRepository {

    /**
     * Returns the next available chunk index for a session.
     * Returns 0 when no chunks exist yet; MAX(chunk_index) + 1 otherwise.
     */
    int nextChunkIndex(String sessionId);

    /**
     * Returns all encoded polyline strings for a session, ordered by chunk_index.
     * Used by the gamification service to compute total route distance.
     */
    List<String> findPolylinesBySessionId(String sessionId);

    /**
     * Persists a single compressed chunk of GPS route data.
     *
     * @param sessionId   the owning walk session
     * @param chunkIndex  monotonically increasing index for this session
     * @param polyline    Google Encoded Polyline string for the batch of coordinates
     * @param timestamps  packed big-endian longs (8 bytes × pointCount) of epoch-ms timestamps
     * @param pointCount  number of GPS points in this chunk
     */
    void saveChunk(String sessionId, int chunkIndex, String polyline,
                   byte[] timestamps, int pointCount);
}
