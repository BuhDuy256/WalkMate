package com.walkmate.presentation.controller.review;

import com.walkmate.application.review.ReviewCommandService;
import com.walkmate.application.review.ReviewQueryService;
import com.walkmate.application.user.UserPrincipal;
import com.walkmate.domain.review.WalkReview;
import com.walkmate.presentation.dto.request.review.SubmitReviewRequest;
import com.walkmate.presentation.dto.response.ApiResponse;
import com.walkmate.presentation.dto.response.review.ReviewResponse;
import com.walkmate.presentation.dto.response.review.ReviewTagResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;

@Tag(name = "Reviews", description = "Post-session review and trust-score management")
@RestController
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewCommandService reviewCommandService;
    private final ReviewQueryService   reviewQueryService;

    /**
     * POST /api/v1/sessions/{sessionId}/review
     *
     * Submits a review for a COMPLETED session. The reviewer must have been a
     * participant. Each participant may review a session exactly once.
     * Atomically updates the reviewee's trust score.
     */
    @PostMapping("/api/v1/sessions/{sessionId}/review")
    public ResponseEntity<ApiResponse<ReviewResponse>> submitReview(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String sessionId,
            @Valid @RequestBody SubmitReviewRequest request) {

        List<java.util.UUID> tagIds = request.tagIds() != null
                ? request.tagIds() : Collections.emptyList();

        WalkReview review = reviewCommandService.submitReview(
                sessionId, principal.userId(), request.ratingStars(), request.comment(), tagIds);

        return ResponseEntity.ok(ApiResponse.success(toResponse(review)));
    }

    /**
     * GET /api/v1/reviews/tags
     *
     * Returns all active tags from the master vocabulary.
     * The client uses {@code tag_type} prefix (POSITIVE_* / NEGATIVE_*) to decide
     * which tags to surface for a given star rating.
     */
    @GetMapping("/api/v1/reviews/tags")
    public ResponseEntity<ApiResponse<List<ReviewTagResponse>>> getReviewTags() {
        List<ReviewTagResponse> tags = reviewQueryService.getActiveTags()
                .stream()
                .map(t -> new ReviewTagResponse(t.tagId(), t.tagName(), t.tagType()))
                .toList();
        return ResponseEntity.ok(ApiResponse.success(tags));
    }

    /**
     * GET /api/v1/users/{userId}/reviews
     *
     * Returns all reviews written about a user (most recent first).
     */
    @GetMapping("/api/v1/users/{userId}/reviews")
    public ResponseEntity<ApiResponse<List<ReviewResponse>>> getUserReviews(
            @PathVariable String userId) {

        List<ReviewResponse> responses = reviewQueryService.getReviewsForUser(userId)
                .stream()
                .map(this::toResponse)
                .toList();

        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ReviewResponse toResponse(WalkReview review) {
        return new ReviewResponse(
                review.getReviewId(),
                review.getSessionId(),
                review.getReviewerId(),
                review.getRevieweeId(),
                review.getRatingStars(),
                review.getComment(),
                review.getCreatedAt().toString()
        );
    }
}
