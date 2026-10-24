package com.walkmate.infrastructure.repository.tracking;

import com.walkmate.domain.tracking.TrackingChunkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class TrackingChunkJdbcRepository implements TrackingChunkRepository {

    private final JdbcClient jdbcClient;

    @Override
    public int nextChunkIndex(String sessionId, String userId) {
        Integer next = jdbcClient.sql("""
                        SELECT COALESCE(MAX(chunk_index) + 1, 0)
                        FROM session_point_chunks
                        WHERE session_id = :sessionId AND user_id = :userId
                        """)
                .param("sessionId", UUID.fromString(sessionId))
                .param("userId",    UUID.fromString(userId))
                .query(Integer.class)
                .single();
        return next != null ? next : 0;
    }

    @Override
    public List<String> findPolylinesBySessionId(String sessionId) {
        return jdbcClient.sql("""
                        SELECT polyline FROM session_point_chunks
                        WHERE session_id = :sessionId
                        ORDER BY chunk_index ASC
                        """)
                .param("sessionId", UUID.fromString(sessionId))
                .query(String.class)
                .list();
    }

    @Override
    public List<String> findPolylinesBySessionAndUser(String sessionId, String userId) {
        return jdbcClient.sql("""
                        SELECT polyline FROM session_point_chunks
                        WHERE session_id = :sessionId AND user_id = :userId
                        ORDER BY chunk_index ASC
                        """)
                .param("sessionId", UUID.fromString(sessionId))
                .param("userId",    UUID.fromString(userId))
                .query(String.class)
                .list();
    }

    @Override
    public int countChunks(String sessionId, String userId) {
        return jdbcClient.sql("""
                        SELECT COUNT(*) FROM session_point_chunks
                        WHERE session_id = :sessionId AND user_id = :userId
                        """)
                .param("sessionId", UUID.fromString(sessionId))
                .param("userId",    UUID.fromString(userId))
                .query(Integer.class)
                .single();
    }

    @Override
    public List<ChunkRow> findChunksAfterIndex(String sessionId, String userId, int afterChunkIndex) {
        return jdbcClient.sql("""
                        SELECT chunk_index,
                               polyline,
                               stream_id,
                               seq_start,
                               seq_end,
                               (EXTRACT(EPOCH FROM COALESCE(received_at, created_at)) * 1000)::bigint
                                   AS created_at_ms
                        FROM session_point_chunks
                        WHERE session_id = :sessionId
                          AND user_id    = :userId
                          AND chunk_index > :afterChunkIndex
                        ORDER BY chunk_index ASC
                        """)
                .param("sessionId",      UUID.fromString(sessionId))
                .param("userId",         UUID.fromString(userId))
                .param("afterChunkIndex", afterChunkIndex)
                .query((rs, rowNum) -> {
                    String streamId = rs.getString("stream_id");
                    Long seqStart = rs.getObject("seq_start") != null ? rs.getLong("seq_start") : null;
                    Long seqEnd   = rs.getObject("seq_end")   != null ? rs.getLong("seq_end")   : null;
                    return new ChunkRow(
                            rs.getInt("chunk_index"),
                            rs.getString("polyline"),
                            streamId,
                            seqStart,
                            seqEnd,
                            rs.getLong("created_at_ms"));
                })
                .list();
    }

    @Override
    public boolean hasLegacyChunks(String sessionId, String userId) {
        Boolean present = jdbcClient.sql("""
                        SELECT EXISTS (
                            SELECT 1 FROM session_point_chunks
                             WHERE session_id = :sessionId
                               AND user_id    = :userId
                               AND stream_id IS NULL
                        )
                        """)
                .param("sessionId", UUID.fromString(sessionId))
                .param("userId",    UUID.fromString(userId))
                .query(Boolean.class)
                .single();
        return Boolean.TRUE.equals(present);
    }

    @Override
    public void saveChunk(String sessionId, String userId, int chunkIndex, String polyline,
                          byte[] timestamps, int pointCount, String syncRequestId) {
        jdbcClient.sql("""
                        INSERT INTO session_point_chunks
                            (session_id, user_id, chunk_index, polyline, timestamps,
                             point_count, sync_request_id)
                        VALUES
                            (:sessionId, :userId, :chunkIndex, :polyline, :timestamps,
                             :pointCount, :syncRequestId)
                        """)
                .param("sessionId",     UUID.fromString(sessionId))
                .param("userId",        UUID.fromString(userId))
                .param("chunkIndex",    chunkIndex)
                .param("polyline",      polyline)
                .param("timestamps",    timestamps)
                .param("pointCount",    pointCount)
                .param("syncRequestId", UUID.fromString(syncRequestId))
                .update();
    }

    // ── v2 (GPS stream) ───────────────────────────────────────────────────────

    @Override
    public List<String> findV2PolylinesBySessionAndUser(String sessionId, String userId) {
        return jdbcClient.sql("""
                        SELECT polyline FROM session_point_chunks
                        WHERE session_id = :sessionId
                          AND user_id    = :userId
                          AND stream_id IS NOT NULL
                        ORDER BY seq_start ASC, chunk_index ASC
                        """)
                .param("sessionId", UUID.fromString(sessionId))
                .param("userId",    UUID.fromString(userId))
                .query(String.class)
                .list();
    }

    @Override
    public boolean existsV2BatchBySyncRequestId(String syncRequestId) {
        Boolean present = jdbcClient.sql("""
                        SELECT 1
                        FROM session_point_chunks
                        WHERE sync_request_id = :syncRequestId
                        """)
                .param("syncRequestId", UUID.fromString(syncRequestId))
                .query(Boolean.class)
                .optional()
                .isPresent();
        return present;
    }

    @Override
    public java.util.Optional<CommittedBatchRow> findCommittedBatch(String syncRequestId) {
        return jdbcClient.sql("""
                        SELECT stream_id,
                               session_id,
                               user_id,
                               seq_start,
                               seq_end,
                               payload_hash,
                               point_count
                        FROM session_point_chunks
                        WHERE sync_request_id = :syncRequestId
                        """)
                .param("syncRequestId", UUID.fromString(syncRequestId))
                .query((rs, rowNum) -> new CommittedBatchRow(
                        UUID.fromString(rs.getString("stream_id")).toString(),
                        UUID.fromString(rs.getString("session_id")).toString(),
                        UUID.fromString(rs.getString("user_id")).toString(),
                        rs.getLong("seq_start"),
                        rs.getLong("seq_end"),
                        rs.getString("payload_hash"),
                        rs.getInt("point_count")))
                .optional();
    }

    @Override
    public boolean trySaveV2Chunk(String sessionId, String userId, String streamId,
                                  int chunkIndex, String polyline, byte[] timestamps,
                                  int pointCount, long seqStart, long seqEnd,
                                  String payloadHashHex, String syncRequestId) {
        // ON CONFLICT (sync_request_id) DO NOTHING RETURNING chunk_id:
        //   - first attempt inserts and returns chunk_id (inserted = true)
        //   - replay of the same batch_id is a no-op (inserted = false)
        // Any other constraint violation propagates and rolls the entire
        // caller transaction back — never ACKs an uncommitted row.
        Boolean inserted = jdbcClient.sql("""
                        INSERT INTO session_point_chunks
                            (session_id, user_id, chunk_index, polyline, timestamps,
                             point_count, sync_request_id, stream_id, seq_start, seq_end,
                             payload_hash)
                        VALUES
                            (:sessionId, :userId, :chunkIndex, :polyline, :timestamps,
                             :pointCount, :syncRequestId, :streamId, :seqStart, :seqEnd,
                             :payloadHash)
                        ON CONFLICT (sync_request_id) DO NOTHING
                        RETURNING chunk_id IS NOT NULL
                        """)
                .param("sessionId",     UUID.fromString(sessionId))
                .param("userId",        UUID.fromString(userId))
                .param("chunkIndex",    chunkIndex)
                .param("polyline",      polyline)
                .param("timestamps",    timestamps)
                .param("pointCount",    pointCount)
                .param("syncRequestId", UUID.fromString(syncRequestId))
                .param("streamId",      UUID.fromString(streamId))
                .param("seqStart",      seqStart)
                .param("seqEnd",        seqEnd)
                .param("payloadHash",   payloadHashHex)
                .query(Boolean.class)
                .optional()
                .orElse(false);
        return inserted;
    }
}
