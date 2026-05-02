package com.walkmate.infrastructure.repository.review;

import com.walkmate.domain.review.WalkReview;
import com.walkmate.domain.review.WalkReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class WalkReviewJdbcRepository implements WalkReviewRepository {

    private final JdbcClient jdbcClient;

    @Override
    public WalkReview save(WalkReview review) {
        jdbcClient.sql("""
                        INSERT INTO walk_review
                            (review_id, session_id, reviewer_id, reviewee_id,
                             rating_stars, comment, created_at)
                        VALUES
                            (:reviewId, :sessionId, :reviewerId, :revieweeId,
                             :ratingStars, :comment, :createdAt)
                        """)
                .param("reviewId",    UUID.fromString(review.getReviewId()))
                .param("sessionId",   UUID.fromString(review.getSessionId()))
                .param("reviewerId",  UUID.fromString(review.getReviewerId()))
                .param("revieweeId",  UUID.fromString(review.getRevieweeId()))
                .param("ratingStars", (short) review.getRatingStars())
                .param("comment",     review.getComment())
                .param("createdAt",   java.sql.Timestamp.from(review.getCreatedAt()))
                .update();
        return review;
    }

    @Override
    public boolean existsBySessionAndReviewer(String sessionId, String reviewerId) {
        Integer count = jdbcClient.sql("""
                        SELECT COUNT(*) FROM walk_review
                        WHERE session_id  = :sessionId
                          AND reviewer_id = :reviewerId
                        """)
                .param("sessionId",  UUID.fromString(sessionId))
                .param("reviewerId", UUID.fromString(reviewerId))
                .query(Integer.class)
                .single();
        return count != null && count > 0;
    }

    @Override
    public List<WalkReview> findByRevieweeId(String revieweeId) {
        return jdbcClient.sql(selectAll() + "WHERE reviewee_id = :revieweeId ORDER BY created_at DESC")
                .param("revieweeId", UUID.fromString(revieweeId))
                .query((rs, rowNum) -> mapRow(rs))
                .list();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String selectAll() {
        return """
                SELECT review_id::text, session_id::text,
                       reviewer_id::text, reviewee_id::text,
                       rating_stars, comment, created_at
                FROM walk_review
                """;
    }

    private WalkReview mapRow(ResultSet rs) throws SQLException {
        return new WalkReview(
                rs.getString("review_id"),
                rs.getString("session_id"),
                rs.getString("reviewer_id"),
                rs.getString("reviewee_id"),
                rs.getInt("rating_stars"),
                rs.getString("comment"),
                rs.getTimestamp("created_at").toInstant()
        );
    }
}
