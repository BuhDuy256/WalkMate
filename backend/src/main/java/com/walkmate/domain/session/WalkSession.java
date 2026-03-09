package com.walkmate.domain.session;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;


/**
 * FULL IMPLEMENTATION: WalkSession Aggregate Root enforcing the new 5-State Machine Design.
 */
@Entity
@Table(name = "walk_session")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // For JPA
public class WalkSession {

    @Id
    @Column(name = "session_id", updatable = false, nullable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SessionStatus status;

    @Column(name = "user1_id", nullable = false, updatable = false)
    private UUID participant1;

    @Column(name = "user2_id", nullable = false, updatable = false)
    private UUID participant2;

    @Column(name = "scheduled_start_time", nullable = false)
    private LocalDateTime scheduledStartTime;

    @Column(name = "scheduled_end_time", nullable = false)
    private LocalDateTime scheduledEndTime;

    @Column(name = "actual_start_time")
    private LocalDateTime actualStartTime;

    @Column(name = "actual_end_time")
    private LocalDateTime actualEndTime;

    @Column(name = "source_intent_id_a", updatable = false)
    private UUID sourceIntentIdA;

    @Column(name = "source_intent_id_b", updatable = false)
    private UUID sourceIntentIdB;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Version
    @Column(name = "version")
    private Long version;

    public WalkSession(UUID id, UUID p1, UUID p2, LocalDateTime scheduledStartTime, LocalDateTime scheduledEndTime, UUID sourceIntentA, UUID sourceIntentB) {
        this.id = id;
        this.participant1 = p1;
        this.participant2 = p2;
        this.scheduledStartTime = scheduledStartTime;
        this.scheduledEndTime = scheduledEndTime;
        this.sourceIntentIdA = sourceIntentA;
        this.sourceIntentIdB = sourceIntentB;
        this.status = SessionStatus.PENDING; 
    }

    // --- STATE MACHINE DOMAIN BEHAVIORS ---

    public void activate(UUID userId, LocalDateTime currentTime) {
        if (this.status != SessionStatus.PENDING) {
            throw new IllegalStateException("Can only activate from PENDING state");
        }
        if (!isParticipant(userId)) {
             throw new IllegalArgumentException("User is not a participant of this Walk Session");
        }

        LocalDateTime windowStart = scheduledStartTime.minusMinutes(15);
        LocalDateTime windowEnd = scheduledStartTime.plusMinutes(30);

        if (currentTime.isBefore(windowStart) || currentTime.isAfter(windowEnd)) {
            throw new IllegalStateException("Outside activation window: You can only activate between 15min early and 30min late");
        }

        this.status = SessionStatus.ACTIVE;
        this.actualStartTime = currentTime;
    }

    public void complete(UUID userId, LocalDateTime currentTime) {
        if (this.status != SessionStatus.ACTIVE) {
            throw new IllegalStateException("Can only complete from ACTIVE state");
        }
        if (!isParticipant(userId)) {
             throw new IllegalArgumentException("User is not a participant of this Walk Session");
        }

        Duration actualDuration = Duration.between(actualStartTime, currentTime);
        if (actualDuration.toMinutes() < 5) {
            throw new IllegalStateException("Walk too short to complete (minimum 5 minutes)");
        }

        this.status = SessionStatus.COMPLETED;
        this.actualEndTime = currentTime;
    }

    public void cancel(UUID userId, String reason, LocalDateTime currentTime) {
        if (this.status != SessionStatus.PENDING) {
            throw new IllegalStateException("Can only cancel from PENDING state");
        }
        if (!isParticipant(userId)) {
             throw new IllegalArgumentException("User is not a participant of this Walk Session");
        }

        this.status = SessionStatus.CANCELLED;
        // The Application Service will listen to the event/result from here to update TrustScore table.
        // E.g. penalty depends on: Duration.between(currentTime, scheduledStartTime).toHours()
    }

    public void reportNoShow(UUID reportingUser, UUID absentUser, LocalDateTime currentTime) {
        if (this.status != SessionStatus.ACTIVE) {
            throw new IllegalStateException("Can only report no-show from ACTIVE state");
        }
        if (!isParticipant(reportingUser)) {
             throw new IllegalArgumentException("User is not a participant of this Walk Session");
        }
        if (reportingUser.equals(absentUser)) {
            throw new IllegalArgumentException("Cannot report yourself as no-show");
        }

        Duration timeSinceActivation = Duration.between(actualStartTime, currentTime);
        if (timeSinceActivation.toMinutes() > 15) {
            throw new IllegalStateException("Too late to report no-show (must be within 15 minutes of activation)");
        }

        this.status = SessionStatus.NO_SHOW;
    }

    public void autoNoShow(LocalDateTime currentTime) {
        if (this.status != SessionStatus.PENDING) {
            throw new IllegalStateException("Auto no-show only from PENDING");
        }
        LocalDateTime windowEnd = scheduledStartTime.plusMinutes(30);
        if (currentTime.isBefore(windowEnd)) {
            throw new IllegalStateException("Still within activation window, cannot auto no-show yet");
        }

        this.status = SessionStatus.NO_SHOW;
    }

    public void autoComplete(LocalDateTime currentTime, CompletionReason reason) {
        if (this.status != SessionStatus.ACTIVE) {
            throw new IllegalStateException("Auto-complete only from ACTIVE");
        }

        this.status = SessionStatus.COMPLETED;
        this.actualEndTime = currentTime;
    }

    public boolean isParticipant(UUID userId) {
        return participant1.equals(userId) || participant2.equals(userId);
    }
}
