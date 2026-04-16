package com.walkmate.application.review;

import com.walkmate.application.gamification.BadgeEvaluationService;
import com.walkmate.domain.review.ReviewErrorCode;
import com.walkmate.domain.review.SessionOutcome;
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

import java.util.List;

@Service
@RequiredArgsConstructor
public class ReviewCommandService {

    private final WalkSessionRepository  walkSessionRepository;
    private final WalkReviewRepository   walkReviewRepository;
    private final UserRepository         userRepository;
    private final BadgeEvaluationService badgeEvaluationService;

    /**
     * Submits a review for a completed walk session.
     *
     * <p>All five steps execute inside a single database transaction so that
     * the review row and the trust-score update are either both committed or
     * both rolled back — no partial state is ever visible.</p>
     *
     * @param sessionId   the session being reviewed
     * @param reviewerId  the authenticated user submitting the review
     * @param ratingStars 1–5 star rating (validated by domain constructor)
     * @param comment     optional free-text comment
     * @return the persisted {@link WalkReview}
     */
    @Transactional
    public WalkReview submitReview(String sessionId, String reviewerId,
                                   int ratingStars, String comment) {

        // 1. Load session and verify it is COMPLETED
        WalkSession session = walkSessionRepository.findById(sessionId)
                .orElseThrow(() -> new DomainException(SessionErrorCode.SESSION_NOT_FOUND));

        // TODO: Commend just to test, Uncomment when review feature is ready
        // if (session.getStatus() != SessionStatus.COMPLETED) {
        //     throw new DomainException(ReviewErrorCode.REVIEW_SESSION_NOT_COMPLETED);
        // }

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

        // 6. Apply the trust-score adjustment for the session outcome
        SessionOutcome outcome = toOutcome(session.getStatus());
        User reviewee = userRepository.findById(revieweeId)
                .orElseThrow(() -> new DomainException(UserErrorCode.USER_NOT_FOUND));

        int newScore = TrustScorePolicy.apply(reviewee.getTrustScore(), outcome);
        reviewee.applyTrustScore(newScore);
        userRepository.save(reviewee);
        badgeEvaluationService.evaluateAndAward(reviewee);

        return review;
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<WalkReview> getReviewsForUser(String userId) {
        return walkReviewRepository.findByRevieweeId(userId);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static SessionOutcome toOutcome(SessionStatus status) {
        switch (status) {
            case COMPLETED: return SessionOutcome.COMPLETED;
            case NO_SHOW:   return SessionOutcome.NO_SHOW;
            case ABORTED:   return SessionOutcome.ABORTED;
            case CANCELLED: return SessionOutcome.CANCELLED;
            default:        return SessionOutcome.COMPLETED;
        }
    }
}
