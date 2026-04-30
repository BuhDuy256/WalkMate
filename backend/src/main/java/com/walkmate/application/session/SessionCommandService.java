package com.walkmate.application.session;

import com.walkmate.application.gamification.SessionCancelledEvent;
import com.walkmate.application.gamification.SessionCompletedEvent;
import com.walkmate.application.gamification.SessionNoShowEvent;
import com.walkmate.domain.notification.Notification;
import com.walkmate.domain.notification.NotificationType;
import com.walkmate.domain.chat.ChatRoomRepository;
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

    /** Minimum walk duration before a user-initiated complete is allowed (S-5). */
    private static final Duration MIN_WALK_DURATION = Duration.ofSeconds(10);

    /** After this duration past scheduledEnd the scheduler auto-completes the session (S-6). */
    private static final Duration MAX_ACTIVE_LIFESPAN = Duration.ofHours(4);

    private final WalkSessionRepository     sessionRepository;
    private final SessionQrTokenProvider    qrTokenProvider;
    private final ApplicationEventPublisher eventPublisher;
    private final NotificationPublisher     notificationPublisher;
    private final ChatRoomRepository        chatRoomRepository;

    @Value("${walkmate.session.pending-ttl:PT24H}")
    private Duration pendingSessionTtl;

    // ── Queries ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<WalkSession> getActiveSessions(String userId) {
        return sessionRepository.findActiveForUser(userId);
    }

    // ── Activate (arrive at meeting point) ────────────────────────────────────

    /**
     * Records the caller's independent arrival. The caller's personal status
     * transitions to ACTIVE immediately (S-2 revised). The global session
     * status also becomes ACTIVE on the first arrival, unblocking the UI.
     */
    @Transactional
    public WalkSession activateSession(String sessionId, String callerId) {
        WalkSession session = loadAndVerifyParticipant(sessionId, callerId);

        Instant now = Instant.now();
        SessionStatus prevStatus = session.getStatus();

        session.recordActivation(callerId, now);
        sessionRepository.save(session);
        sessionRepository.logStateChange(sessionId, prevStatus, session.getStatus(), callerId, "User arrived");

        // Notify as soon as this participant goes ACTIVE — their partner may still be on the way.
        String partnerId = session.getUserIdA().equals(callerId)
                ? session.getUserIdB() : session.getUserIdA();
        Map<String, Object> payload = Map.of("sessionId", sessionId);
        // Tell the activating user they can start walking.
        notificationPublisher.publish(Notification.create(callerId, NotificationType.SESSION_ACTIVE, payload));
        // Tell the partner that the other user has arrived.
        notificationPublisher.publish(Notification.create(partnerId, NotificationType.SESSION_ACTIVE, payload));

        return session;
    }

    // ── Cancel (before either participant has started) ────────────────────────

    /**
     * Cancels a PENDING session. Only valid when the global status is still
     * PENDING (i.e. neither participant has activated yet).
     */
    @Transactional
    public void cancelSession(String sessionId, String callerId, String reason) {
        WalkSession session = loadAndVerifyParticipant(sessionId, callerId);
        Instant now = Instant.now();
        session.cancel(reason, callerId);
        sessionRepository.save(session);
        sessionRepository.logStateChange(sessionId, SessionStatus.PENDING, SessionStatus.CANCELLED,
                callerId, reason);

        // Publish cancellation event so GamificationCommandService can apply
        // the correct time-based penalty (CANCELLED_LATE or CANCELLED_EARLY).
        eventPublisher.publishEvent(
                new SessionCancelledEvent(sessionId, callerId, session.getScheduledStart(), now));

        // S-7: lock the chat room after PostgreSQL commits.
        final String sid = session.getSessionId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try { chatRoomRepository.closeRoom(sid); }
                catch (Exception e) { log.error("Chat room close failed on cancelSession: sessionId={}", sid, e); }
            }
        });
    }

    // ── Complete (per-participant, user-initiated) ─────────────────────────────

    /**
     * Marks the calling participant's walk as COMPLETED (S-5 revised).
     *
     * <p>Validation is against the caller's <em>personal</em> status
     * ({@code user_a_status} / {@code user_b_status}), NOT the derived global
     * {@code status}. Per the State Invariant Table, the global status remains
     * ACTIVE while the partner is still walking, so blocking on GLOBAL would
     * prevent User B from completing after User A finishes — which is the bug
     * this guard explicitly prevents.
     *
     * <p>Side-effects (gamification, chat close, review notifications) fire only
     * when BOTH participants reach a terminal state ({@code global == COMPLETED}).
     */
    @Transactional
    public WalkSession completeSession(String sessionId, String callerId) {
        WalkSession session = loadAndVerifyParticipant(sessionId, callerId);

        Instant now = Instant.now();

        // Guard: validate PERSONAL STATE, not GLOBAL STATE.
        // This allows User B to complete independently even after User A has
        // already finished (global would still be ACTIVE at that point).
        SessionStatus callerPersonalStatus = session.getUserIdA().equals(callerId)
                ? session.getUserAStatus() : session.getUserBStatus();
        if (callerPersonalStatus != SessionStatus.ACTIVE) {
            throw new DomainException(SessionErrorCode.SESSION_NOT_ACTIVE);
        }

        // Enforce minimum walk duration per participant, using their individual start time.
        Instant userStartedAt = session.getUserIdA().equals(callerId)
                ? session.getUserAActivatedAt() : session.getUserBActivatedAt();
        if (userStartedAt != null
                && Duration.between(userStartedAt, now).compareTo(MIN_WALK_DURATION) < 0) {
            throw new DomainException(SessionErrorCode.SESSION_COMPLETE_TOO_EARLY);
        }

        SessionStatus prevStatus = session.getStatus();
        // session.complete() re-checks personal status and calls deriveGlobalStatus()
        // to update the global state based on the State Invariant Table.
        session.complete(callerId, now);
        sessionRepository.save(session);
        sessionRepository.logStateChange(sessionId, prevStatus, session.getStatus(),
                callerId, "User completed walk");

        // Fire terminal-state side-effects only when the global session is done.
        if (session.getStatus() == SessionStatus.COMPLETED) {
            eventPublisher.publishEvent(new SessionCompletedEvent(session));

            final String sid = session.getSessionId();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try { chatRoomRepository.closeRoom(sid); }
                    catch (Exception e) { log.error("Chat room close failed on completeSession: sessionId={}", sid, e); }
                }
            });

            String partnerId = session.getUserIdA().equals(callerId)
                    ? session.getUserIdB() : session.getUserIdA();
            Map<String, Object> reviewPayload = Map.of("sessionId", sessionId);
            notificationPublisher.publish(Notification.create(callerId,  NotificationType.REVIEW_REQUESTED, reviewPayload));
            notificationPublisher.publish(Notification.create(partnerId, NotificationType.REVIEW_REQUESTED, reviewPayload));
        }

        return session;
    }

    // ── Scheduled lifecycle sweep ─────────────────────────────────────────────

    /**
     * Called every 60 seconds by {@link SessionScheduler}. Handles two rules:
     * - PENDING TTL: unresolved PENDING session older than cutoff → CANCELLED.
     * - S-6 safety ceiling: ACTIVE session past scheduledEnd + maxLifespan → COMPLETED.
     */
    @Transactional
    public void handleExpiredSessions() {
        Instant now = Instant.now();

        // ── NO_SHOW sweep (runs first so resolved sessions leave the PENDING pool) ──
        // Any PENDING session whose scheduled_start is older than ACTIVATION_WINDOW_AFTER
        // (180 min) means the arrival window has fully closed — mark all still-PENDING
        // participants as NO_SHOW.
        Instant noShowCutoff = now.minus(WalkSession.ACTIVATION_WINDOW_AFTER);
        List<WalkSession> noShowSessions = sessionRepository.findPendingSessionsPastNoShowDeadline(noShowCutoff);
        for (WalkSession session : noShowSessions) {
            String noShowSid = session.getSessionId();
            try {
                session.markBothNoShow(now);
                sessionRepository.save(session);
                sessionRepository.logStateChange(noShowSid, SessionStatus.PENDING, SessionStatus.COMPLETED,
                        null, "scheduler-no-show");

                eventPublisher.publishEvent(new SessionNoShowEvent(noShowSid, session.getUserIdA()));
                eventPublisher.publishEvent(new SessionNoShowEvent(noShowSid, session.getUserIdB()));

                log.info("Scheduler: session {} marked both NO_SHOW", noShowSid);
            } catch (Exception e) {
                log.error("Scheduler: NO_SHOW sweep failed for session {}: {}", noShowSid, e.getMessage(), e);
            }
        }

        // PENDING TTL cleanup
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

        // S-6: auto-complete overdue ACTIVE sessions
        Instant cutoff = now.minus(MAX_ACTIVE_LIFESPAN);
        List<WalkSession> overdueActive = sessionRepository.findSessionsPastEndTime(cutoff);
        for (WalkSession session : overdueActive) {
            session.complete(now);  // completes both participants
            sessionRepository.save(session);
            sessionRepository.logStateChange(session.getSessionId(), SessionStatus.ACTIVE, SessionStatus.COMPLETED,
                    null, "scheduler-auto-complete");
            eventPublisher.publishEvent(new SessionCompletedEvent(session));

            final String autoSid = session.getSessionId();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try { chatRoomRepository.closeRoom(autoSid); }
                    catch (Exception e) { log.error("Chat room close failed on scheduler auto-complete: sessionId={}", autoSid, e); }
                }
            });

            Map<String, Object> reviewPayload = Map.of("sessionId", session.getSessionId());
            notificationPublisher.publish(Notification.create(session.getUserIdA(), NotificationType.REVIEW_REQUESTED, reviewPayload));
            notificationPublisher.publish(Notification.create(session.getUserIdB(), NotificationType.REVIEW_REQUESTED, reviewPayload));

            log.info("Scheduler: session {} auto-completed", session.getSessionId());
        }
    }

    // ── QR verification ───────────────────────────────────────────────────────

    /**
     * Generates a short-lived QR token for the caller in an ACTIVE session.
     * The token embeds the caller's userId and the sessionId, signed with HS256.
     */
    @Transactional(readOnly = true)
    public String generateSessionQrToken(String sessionId, String callerId) {
        WalkSession session = loadAndVerifyParticipant(sessionId, callerId);
        if (session.getStatus() != SessionStatus.ACTIVE) {
            throw new DomainException(SessionErrorCode.SESSION_NOT_ACTIVE);
        }
        return qrTokenProvider.generateQrToken(callerId, sessionId);
    }

    /**
     * Verifies the partner's QR token submitted by the caller.
     *
     * <ol>
     *   <li>Validates JWT (signature, expiry, session match, purpose claim).</li>
     *   <li>Rejects self-verification.</li>
     *   <li>Runs domain guards in {@link WalkSession#recordQrVerification} (ACTIVE + not
     *       already verified).</li>
     *   <li>Persists via a targeted atomic UPDATE (IS NULL guard).</li>
     * </ol>
     */
    @Transactional
    public WalkSession verifyPartnerQr(String sessionId, String callerId, String partnerQrToken) {
        String tokenOwnerId = qrTokenProvider.validateQrToken(partnerQrToken, sessionId);
        if (tokenOwnerId.equals(callerId)) {
            throw new DomainException(SessionErrorCode.SESSION_QR_SELF_VERIFICATION);
        }
        WalkSession session = loadAndVerifyParticipant(sessionId, callerId);
        session.recordQrVerification(callerId);
        sessionRepository.recordQrVerification(sessionId, callerId, Instant.now());
        return session;
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
