package com.walkmate.application.gamification;

import com.walkmate.domain.gamification.Badge;
import com.walkmate.domain.gamification.BadgePolicy;
import com.walkmate.domain.gamification.UserBadgeRepository;
import com.walkmate.domain.gamification.UserStats;
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
import java.util.Set;

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
    private final UserBadgeRepository     badgeRepository;
    private final TrackingChunkRepository trackingChunkRepository;
    private final WalkSessionRepository   sessionRepository;

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

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onSessionAborted(SessionAbortedEvent event) {
        try {
            applyPenalty(event.getAbortingUserId(), SessionOutcome.ABORTED);
        } catch (Exception ex) {
            log.error("Gamification penalty failed for ABORTED session={} user={}: {}",
                    event.getSessionId(), event.getAbortingUserId(), ex.getMessage(), ex);
        }
    }

    // ── Core reward logic ─────────────────────────────────────────────────────

    private void rewardBothParticipants(WalkSession session) {
        double distanceKm  = calculateTotalDistanceKm(session);
        int    durationMin = calculateDurationMinutes(session.getStartedAt(), session.getEndedAt());
        int    points      = (int) (distanceKm * 10) + (durationMin * 2);

        log.info("Session {} — dist={}km, dur={}min, points={}",
                session.getSessionId(), String.format("%.2f", distanceKm), durationMin, points);

        // Persist the GPS-derived distance back to the session record so that
        // session history queries can surface it without re-aggregating polylines.
        session.recordFinalDistance(distanceKm);
        sessionRepository.save(session);

        rewardUser(session.getUserIdA(), points, distanceKm);
        rewardUser(session.getUserIdB(), points, distanceKm);
    }

    private void rewardUser(String userId, int points, double distanceKm) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            log.warn("Gamification: user {} not found, skipping reward", userId);
            return;
        }

        user.applySessionReward(points, distanceKm);
        userRepository.save(user);

        UserStats stats = new UserStats(
                userId,
                user.getCompletedSessions(),
                user.getTotalDistanceKm(),
                user.getTotalPoints(),
                user.getTrustScore()
        );

        Set<String>  existingBadges = badgeRepository.findBadgeNamesByUserId(userId);
        List<Badge>  newBadges      = BadgePolicy.evaluateEarned(stats, existingBadges);

        if (!newBadges.isEmpty()) {
            badgeRepository.saveAll(userId, newBadges);
            log.info("Awarded {} new badge(s) to user {}: {}", newBadges.size(), userId, newBadges);
        }
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

    private double calculateTotalDistanceKm(WalkSession session) {
        String sid   = session.getSessionId();
        String idA   = session.getUserIdA();
        String idB   = session.getUserIdB();

        int countA = trackingChunkRepository.countChunks(sid, idA);
        int countB = trackingChunkRepository.countChunks(sid, idB);
        // Use the participant with more chunks (better GPS coverage). Tiebreak: user_id_a.
        String canonicalUserId = (countB > countA) ? idB : idA;

        List<String> polylines = trackingChunkRepository.findPolylinesBySessionAndUser(sid, canonicalUserId);
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
