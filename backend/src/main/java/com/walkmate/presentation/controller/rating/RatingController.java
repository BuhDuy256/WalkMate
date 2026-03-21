package com.walkmate.presentation.controller.rating;

import com.walkmate.application.rating.RatingCommandService;
import com.walkmate.domain.rating.*;
import com.walkmate.presentation.dto.request.rating.SubmitRatingRequest;
import com.walkmate.presentation.dto.response.RatingResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * REST Controller for Rating operations
 */
@RestController
@RequestMapping("/api/ratings")
public class RatingController {

    private final RatingCommandService ratingCommandService;

    public RatingController(RatingCommandService ratingCommandService) {
        this.ratingCommandService = ratingCommandService;
    }

  
    @PostMapping
    public ResponseEntity<RatingResponse> submitRating(@Valid @RequestBody SubmitRatingRequest request) {
        try {
            // Print received data from FE
            System.out.println("[DEBUG] Received rating request: " + request);

            // Map DTO to domain
            System.out.println("[DEBUG] Mapping to domain...");
            Rating rating = mapToDomain(request);
            System.out.println("[DEBUG] Domain rating created: " + rating);

            // Submit rating
            System.out.println("[DEBUG] Calling ratingCommandService.submitRating...");
            // TODO: Fix database connection issue first
            // Rating savedRating = ratingCommandService.submitRating(rating);

            // Mock response for testing without DB
            Rating savedRating = rating;
            savedRating.setReviewId(java.util.UUID.randomUUID());
            savedRating.setCreatedAt(java.time.LocalDateTime.now());
            System.out.println("[DEBUG] Rating saved successfully (MOCK): " + savedRating);

            // Map domain to response
            RatingResponse response = mapToResponse(savedRating);

            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            System.err.println("[ERROR] Exception in submitRating: " + e.getClass().getName());
            System.err.println("[ERROR] Message: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    private Rating mapToDomain(SubmitRatingRequest request) {
        RatingScore score = new RatingScore(request.getScore());

        List<RatingTag> tags = request.getTags() != null
                ? request.getTags().stream()
                .map(RatingTag::fromCode)
                .collect(Collectors.toList())
                : List.of();

        RatingComment comment = new RatingComment(request.getNote());

        return new Rating(
                request.getSessionId(),
                request.getUserId(),
                request.getRevieweeId(),
                score,
                tags,
                comment
        );
    }

    private RatingResponse mapToResponse(Rating rating) {
        List<String> tagCodes = rating.getTags() != null
                ? rating.getTags().stream()
                .map(RatingTag::toCode)
                .collect(Collectors.toList())
                : List.of();

        return new RatingResponse(
                rating.getReviewId(),
                rating.getSessionId(),
                rating.getReviewerId(),
                rating.getRevieweeId(),
                rating.getScore().getValue(),
                tagCodes,
                rating.getComment().getValue(),
                rating.getCreatedAt()
        );
    }
}
