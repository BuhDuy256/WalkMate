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
        Integer count = jdbcClient.sql("""
                        SELECT COUNT(*) FROM session_point_chunks
                        WHERE session_id = :sessionId AND user_id = :userId
                        """)
                .param("sessionId", UUID.fromString(sessionId))
                .param("userId",    UUID.fromString(userId))
                .query(Integer.class)
                .single();
        return count != null ? count : 0;
    }

    @Override
    public void saveChunk(String sessionId, String userId, int chunkIndex, String polyline,
                          byte[] timestamps, int pointCount) {
        jdbcClient.sql("""
                        INSERT INTO session_point_chunks
                            (session_id, user_id, chunk_index, polyline, timestamps, point_count)
                        VALUES
                            (:sessionId, :userId, :chunkIndex, :polyline, :timestamps, :pointCount)
                        """)
                .param("sessionId",  UUID.fromString(sessionId))
                .param("userId",     UUID.fromString(userId))
                .param("chunkIndex", chunkIndex)
                .param("polyline",   polyline)
                .param("timestamps", timestamps)
                .param("pointCount", pointCount)
                .update();
    }
}
