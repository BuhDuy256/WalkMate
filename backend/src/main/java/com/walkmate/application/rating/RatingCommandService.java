package com.walkmate.application.rating;

import com.walkmate.domain.rating.*;
import com.walkmate.domain.shared.exception.DomainException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import javax.sql.DataSource;


@Service
public class RatingCommandService {

    private final RatingRepository ratingRepository;
    private final DataSource dataSource;

    public RatingCommandService(RatingRepository ratingRepository, DataSource dataSource) {
        this.ratingRepository = ratingRepository;
        this.dataSource = dataSource;
    }

    @Transactional
    public Rating submitRating(Rating rating) {
        // Business rule 1: Check if user already rated this session
        if (ratingRepository.existsBySessionAndReviewer(rating.getSessionId(), rating.getReviewerId())) {
            throw new DomainException(
                    RatingErrorCode.RATING_ALREADY_EXISTS.name(),
                    "User has already rated this session"
            );
        }

        // Business rule 2: Validate session exists and is COMPLETED
        validateSessionCompleted(rating.getSessionId(), rating.getReviewerId());

        // Save rating
        return ratingRepository.save(rating);
    }

    private void validateSessionCompleted(UUID sessionId, UUID reviewerId) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT status, user1_id, user2_id FROM walk_session WHERE session_id = ?"
             )) {

            stmt.setObject(1, sessionId);   
            ResultSet rs = stmt.executeQuery();

            if (!rs.next()) {
                throw new DomainException(
                        RatingErrorCode.RATING_SESSION_NOT_FOUND.name(),
                        "Session not found"
                );
            }

            String status = rs.getString("status");
            UUID user1Id = (UUID) rs.getObject("user1_id");
            UUID user2Id = (UUID) rs.getObject("user2_id");

            // Check if session is completed
            if (!"COMPLETED".equals(status)) {
                throw new DomainException(
                        RatingErrorCode.RATING_SESSION_NOT_COMPLETED.name(),
                        "Session is not completed yet"
                );
            }

            // Check if reviewer is part of the session
            if (!reviewerId.equals(user1Id) && !reviewerId.equals(user2Id)) {
                throw new DomainException(
                        RatingErrorCode.RATING_UNAUTHORIZED.name(),
                        "User is not part of this session"
                );
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed to validate session", e);
        }
    }
}
