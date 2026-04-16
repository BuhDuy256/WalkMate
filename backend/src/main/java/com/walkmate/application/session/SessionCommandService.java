package com.walkmate.application.session;

import com.walkmate.application.gamification.SessionAbortedEvent;
import com.walkmate.application.gamification.SessionCompletedEvent;
import com.walkmate.domain.notification.Notification;
import com.walkmate.domain.notification.NotificationType;
import com.walkmate.domain.chat.ChatRoomRepository;
import com.walkmate.domain.session.AbortReason;
import com.walkmate.domain.session.SessionErrorCode;
import com.walkmate.domain.session.SessionStatus;
import com.walkmate.domain.session.WalkSession;
import com.walkmate.domain.session.WalkSessionRepository;
import com.walkmate.domain.shared.NotificationPublisher;
import com.walkmate.domain.shared.exception.DomainException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

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

    private final WalkSessionRepository     sessionRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final NotificationPublisher     notificationPublisher;
    private final ChatRoomRepository        chatRoomRepository;

    /**
     * Safety cleanup TTL for unresolved PENDING sessions.
     * Config key: walkmate.session.pending-ttl (ISO-8601 duration, e.g. PT24H).
     */
    @Value("${walkmate.session.pending-ttl:PT24H}")
    private Duration pendingSessionTtl;

    // ── Queries ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<WalkSession> getActiveSessions(String userId) {
        return sessionRepository.findActiveForUser(userId);
    }

    // ── Activate (arrive at meeting point) ────────────────────────────────────

    /**
     * Records the caller's arrival. Transitions session to ACTIVE once both
     * participants have activated (S-3).
     */
    @Transactional
    public WalkSession activateSession(String sessionId, String callerId) {
        WalkSession session = loadAndVerifyParticipant(sessionId, callerId);

        Instant now = Instant.now();

        SessionStatus prevStatus = session.getStatus();
        session.recordActivation(callerId, now);
        sessionRepository.save(session);
        sessionRepository.logStateChange(sessionId, prevStatus, session.getStatus(), callerId, "User arrived");

        // When both participants have arrived, notify them that the walk is now ACTIVE
        if (session.getStatus() == SessionStatus.ACTIVE) {
            String partnerId = session.getUserIdA().equals(callerId)
                    ? session.getUserIdB() : session.getUserIdA();
            Map<String, Object> payload = Map.of("sessionId", sessionId);
            notificationPublisher.publish(Notification.create(callerId,  NotificationType.SESSION_ACTIVE, payload));
            notificationPublisher.publish(Notification.create(partnerId, NotificationType.SESSION_ACTIVE, payload));
        }

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

        // S-7: lock the chat room after PostgreSQL commits
        final String sid = session.getSessionId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try { chatRoomRepository.closeRoom(sid); }
                catch (Exception e) { log.error("Chat room close failed on cancelSession: sessionId={}", sid, e); }
            }
        });
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
        eventPublisher.publishEvent(new SessionAbortedEvent(session.getSessionId(), callerId));

        // S-7: lock the chat room after PostgreSQL commits
        final String sid = session.getSessionId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try { chatRoomRepository.closeRoom(sid); }
                catch (Exception e) { log.error("Chat room close failed on abortSession: sessionId={}", sid, e); }
            }
        });
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
        eventPublisher.publishEvent(new SessionCompletedEvent(session));

        // S-7: lock the chat room after PostgreSQL commits
        final String sid = session.getSessionId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try { chatRoomRepository.closeRoom(sid); }
                catch (Exception e) { log.error("Chat room close failed on completeSession: sessionId={}", sid, e); }
            }
        });

        // Notify both participants to leave a review
        String partnerId = session.getUserIdA().equals(callerId)
                ? session.getUserIdB() : session.getUserIdA();
        Map<String, Object> reviewPayload = Map.of("sessionId", sessionId);
        notificationPublisher.publish(Notification.create(callerId,  NotificationType.REVIEW_REQUESTED, reviewPayload));
        notificationPublisher.publish(Notification.create(partnerId, NotificationType.REVIEW_REQUESTED, reviewPayload));

        return session;
    }

    // ── Scheduled lifecycle sweep ─────────────────────────────────────────────

    /**
     * Called every 60 seconds by {@link SessionScheduler}. Handles two rules:
     * <ul>
     *   <li><b>PENDING TTL</b> — unresolved PENDING session older than cutoff → CANCELLED.</li>
     *   <li><b>S-9</b> — ACTIVE session past scheduledEnd + maxLifespan → COMPLETED.</li>
     * </ul>
     */
    @Transactional
    public void handleExpiredSessions() {
        Instant now = Instant.now();

        // ── PENDING TTL cleanup: avoid indefinitely lingering unresolved sessions ──
        Instant pendingCutoff = now.minus(pendingSessionTtl);
        List<WalkSession> stalePending = sessionRepository.findStalePendingSessions(pendingCutoff);
        for (WalkSession session : stalePending) {
            session.cancel("Auto-cancelled: pending session exceeded TTL", null);
            sessionRepository.save(session);
            sessionRepository.logStateChange(
                    session.getSessionId(),
                    SessionStatus.PENDING,
                    SessionStatus.CANCELLED,
                    null,
                    "scheduler-pending-ttl"
            );

            final String staleSid = session.getSessionId();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try { chatRoomRepository.closeRoom(staleSid); }
                    catch (Exception e) { log.error("Chat room close failed on pending TTL sweep: sessionId={}", staleSid, e); }
                }
            });

            log.info("Scheduler: session {} auto-cancelled by pending TTL", session.getSessionId());
        }

        // ── S-9: ACTIVE sessions that have run past scheduledEnd + maxLifespan ──
        Instant cutoff = now.minus(MAX_ACTIVE_LIFESPAN);
        List<WalkSession> overdueActive = sessionRepository.findSessionsPastEndTime(cutoff);
        for (WalkSession session : overdueActive) {
            session.complete(now);
            sessionRepository.save(session);
            sessionRepository.logStateChange(session.getSessionId(), SessionStatus.ACTIVE, SessionStatus.COMPLETED,
                    null, "scheduler-auto-complete");
            eventPublisher.publishEvent(new SessionCompletedEvent(session));

            // S-7: lock the chat room after PostgreSQL commits
            final String autoSid = session.getSessionId();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try { chatRoomRepository.closeRoom(autoSid); }
                    catch (Exception e) { log.error("Chat room close failed on scheduler auto-complete: sessionId={}", autoSid, e); }
                }
            });

            // Notify both participants to leave a review (auto-completed walk)
            Map<String, Object> reviewPayload = Map.of("sessionId", session.getSessionId());
            notificationPublisher.publish(Notification.create(session.getUserIdA(), NotificationType.REVIEW_REQUESTED, reviewPayload));
            notificationPublisher.publish(Notification.create(session.getUserIdB(), NotificationType.REVIEW_REQUESTED, reviewPayload));

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
