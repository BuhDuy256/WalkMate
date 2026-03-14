package com.walkmate.domain.session.entity;

import com.walkmate.domain.session.enums.AbortReason;
import com.walkmate.domain.session.enums.SessionStatus;
import com.walkmate.domain.session.exception.DomainRuleException;
import com.walkmate.infrastructure.exception.ErrorCode;
import com.walkmate.domain.session.valueobject.ParticipantState;

import jakarta.persistence.*;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "walk_session")
public class WalkSession {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID sessionId;

    @Version
    private Long version;

    @AttributeOverrides({
        @AttributeOverride(name = "userId", column = @Column(name = "user1_id")),
        @AttributeOverride(name = "activatedAt", column = @Column(name = "user1_activated_at"))
    })
    private ParticipantState participant1;

    @AttributeOverrides({
        @AttributeOverride(name = "userId", column = @Column(name = "user2_id")),
        @AttributeOverride(name = "activatedAt", column = @Column(name = "user2_activated_at"))
    })
    private ParticipantState participant2;

    @Column(nullable = false)
    private Instant scheduledStartTime;

    @Column(nullable = false)
    private Instant scheduledEndTime;

    private Instant actualStartTime;
    private Instant actualEndTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SessionStatus status;

    private String cancellationReason;

    @Enumerated(EnumType.STRING)
    private AbortReason abortReason;

    @Transient
    private static final Duration EARLY_GRACE = Duration.ofMinutes(5);
    @Transient
    private static final Duration LATE_GRACE = Duration.ofMinutes(10);
    @Transient
    private static final Duration MIN_PHYSICAL_DURATION = Duration.ofMinutes(5);

    protected WalkSession() {} // JPA

    public WalkSession(UUID user1Id, UUID user2Id, Instant scheduledStartTime, Instant scheduledEndTime) {
        this.participant1 = new ParticipantState(user1Id);
        this.participant2 = new ParticipantState(user2Id);
        this.scheduledStartTime = scheduledStartTime;
        this.scheduledEndTime = scheduledEndTime;
        this.status = SessionStatus.PENDING;
    }

    // --- Core Intention-Revealing Methods ---

    public void activate(UUID userId, Clock clock) {
        assertNotTerminal();
        if (this.status != SessionStatus.PENDING) {
            throw new DomainRuleException(ErrorCode.SESSION_INVALID_TRANSITION, "Session is not PENDING");
        }

        Instant now = clock.instant();
        if (now.isBefore(scheduledStartTime.minus(EARLY_GRACE)) || now.isAfter(scheduledStartTime.plus(LATE_GRACE))) {
            throw new DomainRuleException(ErrorCode.SESSION_ACTIVATION_WINDOW_CLOSED, "Outside of activation window");
        }

        ParticipantState participant = getParticipant(userId);
        if (participant.isActivated()) {
            return; // Idempotent
        }

        participant.activate(now);

        // Check if both are activated to transition
        if (participant1.isActivated() && participant2.isActivated()) {
            this.status = SessionStatus.ACTIVE;
            this.actualStartTime = now;
        }
    }

    public void cancel(UUID userId, String reason, Clock clock) {
        if (this.status == SessionStatus.CANCELLED) {
            return; // Idempotent handler for simultaneous cancellation
        }
        if (this.status != SessionStatus.PENDING) {
             throw new DomainRuleException(ErrorCode.SESSION_INVALID_TRANSITION, "Can only cancel PENDING sessions");
        }

        Instant now = clock.instant();
        if (now.isAfter(scheduledStartTime.plus(LATE_GRACE))) {
             throw new DomainRuleException(ErrorCode.SESSION_ACTIVATION_WINDOW_CLOSED, "Cancellation window has closed");
        }
        
        getParticipant(userId); // Ensure user belongs to session
        
        this.status = SessionStatus.CANCELLED;
        this.cancellationReason = reason;
        this.actualEndTime = now;
    }

    public void complete(UUID userId, Clock clock) {
        if (this.status == SessionStatus.COMPLETED) {
            return; // Idempotent handler if system cron beat them to it
        }
        assertIsActive();
        getParticipant(userId);
        
        Instant now = clock.instant();
        if (Duration.between(actualStartTime, now).compareTo(MIN_PHYSICAL_DURATION) < 0) {
            throw new DomainRuleException(ErrorCode.SESSION_TOO_SHORT_TO_COMPLETE, "Session duration under minimum threshold");
        }

        this.status = SessionStatus.COMPLETED;
        this.actualEndTime = now;
    }

    public void abort(UUID userId, AbortReason reason, Clock clock) {
        assertIsActive();
        getParticipant(userId);
        
        this.status = SessionStatus.ABORTED;
        this.abortReason = reason;
        this.actualEndTime = clock.instant();
    }

    // --- System Actions ---

    public void systemExpireToNoShowOrCancelled(Clock clock) {
        if (this.status != SessionStatus.PENDING) return;
        
        boolean p1 = participant1.isActivated();
        boolean p2 = participant2.isActivated();

        if (p1 ^ p2) {
            this.status = SessionStatus.NO_SHOW;
        } else if (!p1 && !p2) {
            this.status = SessionStatus.CANCELLED;
        }
        this.actualEndTime = clock.instant();
    }

    public void systemForceComplete(Clock clock) {
        if (this.status == SessionStatus.COMPLETED) return;
        assertIsActive();
        this.status = SessionStatus.COMPLETED;
        this.actualEndTime = clock.instant();
    }

    // --- Private Helpers ---

    private void assertNotTerminal() {
        if (this.status == SessionStatus.COMPLETED || this.status == SessionStatus.CANCELLED 
            || this.status == SessionStatus.NO_SHOW || this.status == SessionStatus.ABORTED) {
            throw new DomainRuleException(ErrorCode.SESSION_INVALID_TRANSITION, "Session is in terminal state");
        }
    }

    private void assertIsActive() {
        if (this.status != SessionStatus.ACTIVE) {
            throw new DomainRuleException(ErrorCode.SESSION_INVALID_TRANSITION, "Session is not ACTIVE");
        }
    }

    private ParticipantState getParticipant(UUID userId) {
        if (participant1.getUserId().equals(userId)) return participant1;
        if (participant2.getUserId().equals(userId)) return participant2;
        throw new DomainRuleException(ErrorCode.SESSION_USER_NOT_PARTICIPANT, "User is not a participant");
    }

    // Getters for mapping
    public UUID getSessionId() { return sessionId; }
    public SessionStatus getStatus() { return status; }
    public Long getVersion() { return version; }
}
