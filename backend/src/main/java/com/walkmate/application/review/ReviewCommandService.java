package com.walkmate.application.review;

import com.walkmate.application.gamification.BadgeEvaluationService;
import com.walkmate.application.walkintent.AiTrainingService;
import com.walkmate.domain.review.ReviewErrorCode;
import com.walkmate.domain.review.ReviewTagRepository;
import com.walkmate.domain.review.TrustScorePolicy;
import com.walkmate.domain.review.WalkReview;
import com.walkmate.domain.review.WalkReviewRepository;
import com.walkmate.domain.session.SessionErrorCode;
import com.walkmate.domain.session.SessionStatus;
import com.walkmate.domain.session.WalkSession;
import com.walkmate.domain.session.WalkSessionRepository;
import com.walkmate.domain.shared.exception.DomainException;
import com.walkmate.domain.user.User;
import com.walkmate.domain.user.UserErrorCode;
import com.walkmate.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReviewCommandService {

    private final WalkSessionRepository walkSessionRepository;
    private final WalkReviewRepository walkReviewRepository;
    private final ReviewTagRepository reviewTagRepository;
    private final UserRepository userRepository;
    private final BadgeEvaluationService badgeEvaluationService;
    private final AiTrainingService aiTrainingService;

    /**
     * Submits a review for a completed walk session (Stage 2 — review-driven
     * scoring).
     *
     * <p>
     * All steps execute inside a single database transaction so that the review
     * row and the trust-score update are either both committed or both rolled back.
     * </p>
     *
     * <p>
     * The trust-score delta is derived from the star rating using a non-linear
     * curve (see {@link #ratingDelta}). This is independent of Stage 1
     * system-driven
     * adjustments already applied by
     * {@link com.walkmate.application.gamification.GamificationCommandService}.
     * </p>
     *
     * @param sessionId   the session being reviewed
     * @param reviewerId  the authenticated user submitting the review
     * @param ratingStars 1–5 star rating (validated by domain constructor)
     * @param comment     optional free-text comment (may be null)
     * @param tagIds      selected tag UUIDs from {@code review_tag_master} (may be
     *                    null/empty)
     * @return the persisted {@link WalkReview}
     */
    @Transactional
    public WalkReview submitReview(String sessionId, String reviewerId,
            int ratingStars, String comment, List<UUID> tagIds) {

        // 1. Load session and verify it is COMPLETED
        WalkSession session = walkSessionRepository.findById(sessionId)
                .orElseThrow(() -> new DomainException(SessionErrorCode.SESSION_NOT_FOUND));

        if (session.getStatus() != SessionStatus.COMPLETED) {
            throw new DomainException(ReviewErrorCode.REVIEW_SESSION_NOT_COMPLETED);
        }

        // 2. Verify the reviewer was a participant
        boolean isParticipant = reviewerId.equals(session.getUserIdA())
                || reviewerId.equals(session.getUserIdB());
        if (!isParticipant) {
            throw new DomainException(ReviewErrorCode.REVIEW_NOT_PARTICIPANT);
        }

        // 3. Guard against duplicate reviews (also enforced by DB unique constraint)
        if (walkReviewRepository.existsBySessionAndReviewer(sessionId, reviewerId)) {
            throw new DomainException(ReviewErrorCode.REVIEW_ALREADY_SUBMITTED);
        }

        // 4. Determine the reviewee (the other participant)
        String revieweeId = reviewerId.equals(session.getUserIdA())
                ? session.getUserIdB()
                : session.getUserIdA();

        // 5. Create and persist the review (domain constructor validates ratingStars)
        WalkReview review = WalkReview.create(sessionId, reviewerId, revieweeId, ratingStars, comment);
        walkReviewRepository.save(review);

        // 6. Apply the Stage-2 trust-score adjustment derived from the star rating.
        // The delta is non-linear: 5★→+10, 4★→+5, 3★→0, 2★→-10, 1★→-20.
        User reviewee = userRepository.findById(revieweeId)
                .orElseThrow(() -> new DomainException(UserErrorCode.USER_NOT_FOUND));

        int newScore = TrustScorePolicy.apply(reviewee.getTrustScore(), ratingDelta(ratingStars));
        reviewee.applyTrustScore(newScore);
        userRepository.save(reviewee);
        badgeEvaluationService.evaluateAndAward(reviewee);

        // 7. Persist structured tag selections (all within the same transaction).
        List<UUID> effectiveTags = (tagIds != null) ? tagIds : Collections.emptyList();
        reviewTagRepository.saveTagMappings(review.getReviewId(), effectiveTags);

        // 8. Async: adjust the reviewer's AI matching weights based on the selected
        // tags.
        // Runs on a separate thread after this transaction commits; failures are
        // non-fatal.
        if (!effectiveTags.isEmpty()) {
            aiTrainingService.trainWeightsFromReview(
                    UUID.fromString(reviewerId),
                    reviewTagRepository.findByIds(effectiveTags));
        }

        return review;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Non-linear star-rating → trust-score delta (Stage 2).
     *
     * 5★ → +10 | 4★ → +5 | 3★ → 0 | 2★ → -10 | 1★ → -20
     */
    private static int ratingDelta(int stars) {
        switch (stars) {
            case 5:
                return +10;
            case 4:
                return +5;
            case 3:
                return 0;
            case 2:
                return -10;
            default:
                return -20; // 1 star
        }
    }
}
