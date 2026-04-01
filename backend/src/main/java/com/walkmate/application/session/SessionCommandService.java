package com.walkmate.application.session;

import com.walkmate.domain.session.AbortReason;
import com.walkmate.domain.session.SessionErrorCode;
import com.walkmate.domain.session.SessionStatus;
import com.walkmate.domain.session.WalkSession;
import com.walkmate.domain.session.WalkSessionRepository;
import com.walkmate.domain.shared.exception.DomainException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SessionCommandService {

    /**
     * Minimum walk duration before a user-initiated complete is allowed (S-7).
     */
    private static final Duration MIN_WALK_DURATION = Duration.ofMinutes(5);

    /**
     * After this duration past scheduledEnd the scheduler auto-completes the session (S-9).
     */
    private static final Duration MAX_ACTIVE_LIFESPAN = Duration.ofHours(4);

    private final WalkSessionRepository sessionRepository;

    // ── Queries ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<WalkSession> getActiveSessions(String userId) {
        return sessionRepository.findActiveForUser(userId);
    }

    // ── Activate (arrive at meeting point) ────────────────────────────────────

    /**
     * Records the caller's arrival. Transitions session to ACTIVE once both
     * participants have activated (S-3). Enforces the arrival window (S-4).
     */
    @Transactional
    public WalkSession activateSession(String sessionId, String callerId) {
        WalkSession session = loadAndVerifyParticipant(sessionId, callerId);

        Instant now         = Instant.now();
        Instant windowOpen  = session.getScheduledStart().minus(WalkSession.ACTIVATION_WINDOW_BEFORE);
        Instant windowClose = session.getScheduledStart().plus(WalkSession.ACTIVATION_WINDOW_AFTER);

        SessionStatus prevStatus = session.getStatus();
        session.recordActivation(callerId, now, windowOpen, windowClose);
        sessionRepository.save(session);
        sessionRepository.logStateChange(sessionId, prevStatus, session.getStatus(), callerId, "User arrived");

        return session;
    }

    // ── Cancel (before walk starts) ───────────────────────────────────────────

    /**
     * Cancels a PENDING session. Either participant may cancel.
     */
    @Transactional
    public void cancelSession(String sessionId, String callerId, String reason) {
        WalkSession session = loadAndVerifyParticipant(sessionId, callerId);
        session.cancel(reason, callerId);
        sessionRepository.save(session);
        sessionRepository.logStateChange(sessionId, SessionStatus.PENDING, SessionStatus.CANCELLED,
                callerId, reason);
    }

    // ── Abort (mid-walk emergency) ────────────────────────────────────────────

    /**
     * Aborts an ACTIVE session mid-walk (C-2 resolution).
     */
    @Transactional
    public void abortSession(String sessionId, String callerId, AbortReason reason) {
        WalkSession session = loadAndVerifyParticipant(sessionId, callerId);
        Instant now = Instant.now();
        session.abort(reason, now);
        sessionRepository.save(session);
        sessionRepository.logStateChange(sessionId, SessionStatus.ACTIVE, SessionStatus.ABORTED,
                callerId, reason.name());
    }

    // ── Complete (user-initiated) ─────────────────────────────────────────────

    /**
     * Marks a session as COMPLETED. Enforces the 5-minute minimum walk guard (S-7).
     */
    @Transactional
    public WalkSession completeSession(String sessionId, String callerId) {
        WalkSession session = loadAndVerifyParticipant(sessionId, callerId);

        Instant now = Instant.now();
        if (session.getStartedAt() != null
                && Duration.between(session.getStartedAt(), now).compareTo(MIN_WALK_DURATION) < 0) {
            throw new DomainException(SessionErrorCode.SESSION_COMPLETE_TOO_EARLY);
        }

        session.complete(now);
        sessionRepository.save(session);
        sessionRepository.logStateChange(sessionId, SessionStatus.ACTIVE, SessionStatus.COMPLETED,
                callerId, "User completed walk");

        return session;
    }

    // ── Scheduled lifecycle sweep ─────────────────────────────────────────────

    /**
     * Called every 60 seconds by {@link SessionScheduler}. Handles three rules:
     * <ul>
     *   <li><b>S-6</b> — PENDING session, 0 activations, window closed → CANCELLED.</li>
     *   <li><b>S-5</b> — PENDING session, 1 activation, window closed  → NO_SHOW.</li>
     *   <li><b>S-9</b> — ACTIVE session past scheduledEnd + maxLifespan → COMPLETED.</li>
     * </ul>
     */
    @Transactional
    public void handleExpiredSessions() {
        Instant now = Instant.now();

        // ── S-5 / S-6: PENDING sessions whose activation window has fully closed ──
        List<WalkSession> expiredPending = sessionRepository.findSessionsPastActivationWindow(now);
        for (WalkSession session : expiredPending) {
            SessionStatus prev      = session.getStatus();
            boolean       aArrived  = session.getUserAActivatedAt() != null;
            boolean       bArrived  = session.getUserBActivatedAt() != null;

            if (!aArrived && !bArrived) {
                // Nobody showed up (S-6)
                session.cancel("Auto-cancelled: no participants arrived within activation window", null);
            } else {
                // Exactly one person showed up (S-5)
                session.markNoShow();
            }

            sessionRepository.save(session);
            sessionRepository.logStateChange(session.getSessionId(), prev, session.getStatus(),
                    null, "scheduler-sweep");
            log.info("Scheduler: session {} transitioned {} → {}",
                    session.getSessionId(), prev, session.getStatus());
        }

        // ── S-9: ACTIVE sessions that have run past scheduledEnd + maxLifespan ──
        Instant cutoff = now.minus(MAX_ACTIVE_LIFESPAN);
        List<WalkSession> overdueActive = sessionRepository.findSessionsPastEndTime(cutoff);
        for (WalkSession session : overdueActive) {
            session.complete(now);
            sessionRepository.save(session);
            sessionRepository.logStateChange(session.getSessionId(), SessionStatus.ACTIVE, SessionStatus.COMPLETED,
                    null, "scheduler-auto-complete");
            log.info("Scheduler: session {} auto-completed", session.getSessionId());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private WalkSession loadAndVerifyParticipant(String sessionId, String callerId) {
        WalkSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new DomainException(SessionErrorCode.SESSION_NOT_FOUND));

        if (!session.getUserIdA().equals(callerId) && !session.getUserIdB().equals(callerId)) {
            throw new DomainException(SessionErrorCode.SESSION_NOT_PARTICIPANT);
        }
        return session;
    }
}
