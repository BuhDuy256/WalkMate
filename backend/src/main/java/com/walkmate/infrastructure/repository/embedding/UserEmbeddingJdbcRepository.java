package com.walkmate.infrastructure.repository.embedding;

import lombok.RequiredArgsConstructor;
import org.postgresql.util.PGobject;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * JDBC repository for the {@code user_embedding} table.
 *
 * <p>Uses {@link PGobject} to read and write the {@code vector} type provided by
 * the pgvector PostgreSQL extension — no additional Java dependency required.
 * The wire format expected by pgvector is: {@code [x1,x2,...,xn]}</p>
 *
 * <p>Typical embedding dimension is 768 (sentence-transformers / BERT-based models).
 * This must match the {@code vector(768)} declaration in the DB schema.</p>
 */
@Repository
@RequiredArgsConstructor
public class UserEmbeddingJdbcRepository {

    /** Must match the vector(N) dimension in V0_init.sql. */
    public static final int VECTOR_DIMENSION = 768;

    private final JdbcClient jdbcClient;

    /**
     * Upserts the embedding vector for a user.
     *
     * @param userId the user whose embedding is being stored
     * @param vector float array of length {@value #VECTOR_DIMENSION}
     * @throws IllegalArgumentException if vector length ≠ {@value #VECTOR_DIMENSION}
     */
    public void save(UUID userId, float[] vector) {
        if (vector.length != VECTOR_DIMENSION) {
            throw new IllegalArgumentException(
                    "Expected vector of dimension " + VECTOR_DIMENSION
                    + " but got " + vector.length);
        }

        jdbcClient.sql("""
                        INSERT INTO user_embedding (user_id, vector_data, last_updated)
                        VALUES (:userId, CAST(:vector AS vector), NOW())
                        ON CONFLICT (user_id) DO UPDATE SET
                            vector_data  = CAST(EXCLUDED.vector_data AS vector),
                            last_updated = EXCLUDED.last_updated
                        """)
                .param("userId", userId)
                .param("vector", toVectorString(vector))
                .update();
    }

    /**
     * Returns the stored embedding for a user, if present.
     *
     * @param userId the user to look up
     * @return the float vector, or empty if not yet computed for this user
     */
    public Optional<float[]> findByUserId(UUID userId) {
        return jdbcClient.sql("""
                        SELECT vector_data::text FROM user_embedding
                        WHERE user_id = :userId
                        """)
                .param("userId", userId)
                .query(String.class)
                .optional()
                .map(UserEmbeddingJdbcRepository::fromVectorString);
    }

    /**
     * Finds the {@code k} users whose stored embeddings are closest to
     * {@code queryVector} by cosine distance (smaller = more similar).
     *
     * <p>Uses pgvector's {@code <=>} cosine-distance operator and the HNSW index
     * defined in the schema, making this sub-millisecond at scale.</p>
     *
     * @param queryVector the probe vector
     * @param k           number of nearest neighbours to return
     * @param excludeUserId  user to exclude from results (typically the querying user)
     * @return UUIDs of the k most similar users, ordered by ascending distance
     */
    public java.util.List<UUID> findKNearestUsers(float[] queryVector, int k, UUID excludeUserId) {
        if (queryVector.length != VECTOR_DIMENSION) {
            throw new IllegalArgumentException(
                    "Expected vector of dimension " + VECTOR_DIMENSION
                    + " but got " + queryVector.length);
        }
        return jdbcClient.sql("""
                        SELECT user_id FROM user_embedding
                        WHERE user_id != :excludeUserId
                        ORDER BY vector_data <=> CAST(:queryVector AS vector)
                        LIMIT :k
                        """)
                .param("excludeUserId", excludeUserId)
                .param("queryVector",   toVectorString(queryVector))
                .param("k",             k)
                .query(UUID.class)
                .list();
    }

    // ── pgvector wire-format helpers ──────────────────────────────────────────

    /**
     * Serialises a float array to the pgvector literal format: {@code [x1,x2,...,xn]}.
     */
    static String toVectorString(float[] vector) {
        return Arrays.stream(vector)
                .mapToObj(f -> String.valueOf((float) f))
                .collect(Collectors.joining(",", "[", "]"));
    }

    /**
     * Parses a pgvector literal back to a float array.
     * Input format from {@code vector_data::text}: {@code [x1,x2,...,xn]}.
     */
    static float[] fromVectorString(String raw) {
        // Strip surrounding brackets
        String stripped = raw.substring(1, raw.length() - 1);
        String[] parts  = stripped.split(",");
        float[]  result = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            result[i] = Float.parseFloat(parts[i].trim());
        }
        return result;
    }
}
