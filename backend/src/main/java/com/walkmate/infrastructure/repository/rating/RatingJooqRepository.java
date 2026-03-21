package com.walkmate.infrastructure.repository.rating;

import com.walkmate.domain.rating.*;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;


@Repository
public class RatingJooqRepository implements RatingRepository {

    private final DataSource dataSource;

    public RatingJooqRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Rating save(Rating rating) {
        UUID reviewId = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.now();

        try (Connection conn = dataSource.getConnection()) {
            // Insert into walk_review
            String insertReviewSql = "INSERT INTO walk_review (review_id, session_id, reviewer_id, reviewee_id, rating_stars, comment, created_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?)";

            try (PreparedStatement stmt = conn.prepareStatement(insertReviewSql)) {
                stmt.setObject(1, reviewId);
                stmt.setObject(2, rating.getSessionId());
                stmt.setObject(3, rating.getReviewerId());
                stmt.setObject(4, rating.getRevieweeId());
                stmt.setInt(5, rating.getScore().getValue());
                stmt.setString(6, rating.getComment().getValue());
                stmt.setTimestamp(7, Timestamp.valueOf(createdAt));

                stmt.executeUpdate();
            }

            // Insert tags
            if (rating.getTags() != null && !rating.getTags().isEmpty()) {
                String insertTagSql = "INSERT INTO review_tag (tag_id, review_id, tag_type) VALUES (?, ?, ?::review_tag_type)";

                try (PreparedStatement stmt = conn.prepareStatement(insertTagSql)) {
                    for (RatingTag tag : rating.getTags()) {
                        stmt.setObject(1, UUID.randomUUID());
                        stmt.setObject(2, reviewId);
                        stmt.setString(3, tag.toCode());
                        stmt.addBatch();
                    }
                    stmt.executeBatch();
                }
            }

            // Return saved rating
            rating.setReviewId(reviewId);
            rating.setCreatedAt(createdAt);
            return rating;

        } catch (SQLException e) {
            throw new RuntimeException("Failed to save rating", e);
        }
    }

    @Override
    public boolean existsBySessionAndReviewer(UUID sessionId, UUID reviewerId) {
        String sql = "SELECT EXISTS(SELECT 1 FROM walk_review WHERE session_id = ? AND reviewer_id = ?)";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, sessionId);
            stmt.setObject(2, reviewerId);

            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getBoolean(1);
            }
            return false;

        } catch (SQLException e) {
            throw new RuntimeException("Failed to check existing rating", e);
        }
    }

    @Override
    public Optional<Rating> findById(UUID reviewId) {
        String sql = "SELECT review_id, session_id, reviewer_id, reviewee_id, rating_stars, comment, created_at " +
                "FROM walk_review WHERE review_id = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, reviewId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return Optional.of(mapToRating(rs));
            }
            return Optional.empty();

        } catch (SQLException e) {
            throw new RuntimeException("Failed to find rating", e);
        }
    }

    @Override
    public Optional<Rating> findBySessionAndReviewer(UUID sessionId, UUID reviewerId) {
        String sql = "SELECT review_id, session_id, reviewer_id, reviewee_id, rating_stars, comment, created_at " +
                "FROM walk_review WHERE session_id = ? AND reviewer_id = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, sessionId);
            stmt.setObject(2, reviewerId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return Optional.of(mapToRating(rs));
            }
            return Optional.empty();

        } catch (SQLException e) {
            throw new RuntimeException("Failed to find rating", e);
        }
    }

    private Rating mapToRating(ResultSet rs) throws SQLException {
        UUID reviewId = (UUID) rs.getObject("review_id");
        UUID sessionId = (UUID) rs.getObject("session_id");
        UUID reviewerId = (UUID) rs.getObject("reviewer_id");
        UUID revieweeId = (UUID) rs.getObject("reviewee_id");
        int ratingStars = rs.getInt("rating_stars");
        String comment = rs.getString("comment");
        LocalDateTime createdAt = rs.getTimestamp("created_at").toLocalDateTime();

        // Load tags
        List<RatingTag> tags = loadTags(reviewId);

        return new Rating(
                reviewId,
                sessionId,
                reviewerId,
                revieweeId,
                new RatingScore(ratingStars),
                tags,
                new RatingComment(comment),
                createdAt
        );
    }

    private List<RatingTag> loadTags(UUID reviewId) throws SQLException {
        String sql = "SELECT tag_type FROM review_tag WHERE review_id = ?";
        List<RatingTag> tags = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, reviewId);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                String tagType = rs.getString("tag_type");
                tags.add(RatingTag.fromCode(tagType));
            }
        }

        return tags;
    }
}
