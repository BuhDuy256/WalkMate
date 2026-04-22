package com.walkmate.application.gamification;

import com.walkmate.domain.review.SessionOutcome;
import com.walkmate.domain.review.TrustScorePolicy;
import com.walkmate.domain.session.WalkSession;
import com.walkmate.domain.session.WalkSessionRepository;
import com.walkmate.domain.tracking.TrackingChunkRepository;
import com.walkmate.domain.user.User;
import com.walkmate.domain.user.UserRepository;
import com.walkmate.infrastructure.util.PolylineDecoder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Handles all gamification side-effects after a session is marked COMPLETED.
 *
 * <p>Listens for {@link SessionCompletedEvent} fired by the session committing
 * transaction. Uses {@code AFTER_COMMIT} so gamification only runs when the
 * session state is durably persisted, and {@code REQUIRES_NEW} to open a
 * completely independent transaction so a gamification failure never rolls back
 * the already-committed session.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GamificationCommandService {

    private final UserRepository          userRepository;
    private final TrackingChunkRepository trackingChunkRepository;
    private final WalkSessionRepository   sessionRepository;
    private final BadgeEvaluationService  badgeEvaluationService;

    // ── Event listener ────────────────────────────────────────────────────────

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onSessionCompleted(SessionCompletedEvent event) {
        WalkSession session = event.getSession();
        try {
            rewardBothParticipants(session);
        } catch (Exception ex) {
            // Log and swallow — gamification must never surface errors to users
            // or affect the completed session record.
            log.error("Gamification failed for session {}: {}", session.getSessionId(), ex.getMessage(), ex);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onSessionNoShow(SessionNoShowEvent event) {
        try {
            applyPenalty(event.getPenalizedUserId(), SessionOutcome.NO_SHOW);
        } catch (Exception ex) {
            log.error("Gamification penalty failed for NO_SHOW session={} user={}: {}",
                    event.getSessionId(), event.getPenalizedUserId(), ex.getMessage(), ex);
        }
    }

    // ── Core reward logic ─────────────────────────────────────────────────────

    private void rewardBothParticipants(WalkSession session) {
        double distKmA = calculateDistanceKm(session.getSessionId(), session.getUserIdA());
        double distKmB = calculateDistanceKm(session.getSessionId(), session.getUserIdB());
        int durMinA = calculateDurationMinutes(session.getUserAActivatedAt(), session.getUserAEndedAt());
        int durMinB = calculateDurationMinutes(session.getUserBActivatedAt(), session.getUserBEndedAt());

        log.info("Session {} — A: dist={}km dur={}min | B: dist={}km dur={}min",
                session.getSessionId(),
                String.format("%.2f", distKmA), durMinA,
                String.format("%.2f", distKmB), durMinB);

        // Persist GPS-derived distances per participant so history queries don't re-aggregate.
        session.recordFinalDistance(session.getUserIdA(), distKmA);
        session.recordFinalDistance(session.getUserIdB(), distKmB);
        sessionRepository.save(session);

        rewardUser(session.getUserIdA(), (int) (distKmA * 10) + (durMinA * 2), distKmA);
        rewardUser(session.getUserIdB(), (int) (distKmB * 10) + (durMinB * 2), distKmB);
    }

    private void rewardUser(String userId, int points, double distanceKm) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            log.warn("Gamification: user {} not found, skipping reward", userId);
            return;
        }

        user.applySessionReward(points, distanceKm);
        userRepository.save(user);

        badgeEvaluationService.evaluateAndAward(user);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void applyPenalty(String userId, SessionOutcome outcome) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            log.warn("Gamification: user {} not found, skipping penalty", userId);
            return;
        }
        int newScore = TrustScorePolicy.apply(user.getTrustScore(), outcome);
        user.applyTrustScore(newScore);
        userRepository.save(user);
        log.info("Gamification: applied {} penalty ({}) to user {} — new trustScore={}",
                outcome.name(), outcome.getDelta(), userId, newScore);
    }

    private double calculateDistanceKm(String sessionId, String userId) {
        List<String> polylines = trackingChunkRepository.findPolylinesBySessionAndUser(sessionId, userId);
        if (polylines.isEmpty()) return 0.0;
        return polylines.stream()
                .mapToDouble(PolylineDecoder::calculateDistanceKm)
                .sum();
    }

    private int calculateDurationMinutes(Instant startedAt, Instant endedAt) {
        if (startedAt == null || endedAt == null) return 0;
        long seconds = Duration.between(startedAt, endedAt).getSeconds();
        return (int) Math.max(0, seconds / 60);
    }
}
