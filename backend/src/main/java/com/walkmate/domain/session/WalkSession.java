package com.walkmate.domain.session;

import com.walkmate.domain.valueobject.SessionTrackingStats;
import com.walkmate.infrastructure.exception.ErrorCode;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class WalkSession {
  private static final Duration EARLY_GRACE = Duration.ofMinutes(5);
  private static final Duration LATE_GRACE = Duration.ofMinutes(10);
  private static final Duration MIN_PHYSICAL_DURATION = Duration.ofMinutes(5);
  private static final Duration MAX_PHYSICAL_DURATION = Duration.ofHours(4);

  private final UUID sessionId;
  private final UUID user1Id;
  private final UUID user2Id;
  private final Instant scheduledStartTime;
  private final Instant scheduledEndTime;
  private Instant user1ActivatedAt;
  private Instant user2ActivatedAt;
  private Instant actualStartTime;
  private Instant actualEndTime;
  private SessionStatus status;
  private BigDecimal totalDistance;
  private long totalDuration;
  private String cancellationReason;
  private UUID cancelledBy;
  private AbortReason abortReason;
  private long version;

  public WalkSession(
      UUID sessionId,
      UUID user1Id,
      UUID user2Id,
      Instant scheduledStartTime,
      Instant scheduledEndTime,
      Instant user1ActivatedAt,
      Instant user2ActivatedAt,
      Instant actualStartTime,
      Instant actualEndTime,
      SessionStatus status,
      BigDecimal totalDistance,
      long totalDuration,
      String cancellationReason,
      UUID cancelledBy,
      AbortReason abortReason,
      long version) {
    this.sessionId = Objects.requireNonNull(sessionId);
    this.user1Id = Objects.requireNonNull(user1Id);
    this.user2Id = Objects.requireNonNull(user2Id);
    this.scheduledStartTime = Objects.requireNonNull(scheduledStartTime);
    this.scheduledEndTime = Objects.requireNonNull(scheduledEndTime);
    this.user1ActivatedAt = user1ActivatedAt;
    this.user2ActivatedAt = user2ActivatedAt;
    this.actualStartTime = actualStartTime;
    this.actualEndTime = actualEndTime;
    this.status = Objects.requireNonNull(status);
    this.totalDistance = totalDistance == null ? BigDecimal.ZERO : totalDistance;
    this.totalDuration = totalDuration;
    this.cancellationReason = cancellationReason;
    this.cancelledBy = cancelledBy;
    this.abortReason = abortReason;
    this.version = version;

    if (this.user1Id.equals(this.user2Id)) {
      throw new DomainException(ErrorCode.SESSION_INVALID_TRANSITION, "session participants must be different");
    }
    if (!this.scheduledEndTime.isAfter(this.scheduledStartTime)) {
      throw new DomainException(ErrorCode.SESSION_INVALID_TRANSITION, "scheduled time window is invalid");
    }
  }

  public void activate(UUID userId, Instant now) {
    assertNotTerminal();
    if (status != SessionStatus.PENDING) {
      throw new DomainException(ErrorCode.SESSION_INVALID_TRANSITION, "only PENDING can be activated");
    }
    if (now.isBefore(scheduledStartTime.minus(EARLY_GRACE)) || now.isAfter(scheduledStartTime.plus(LATE_GRACE))) {
      throw new DomainException(ErrorCode.SESSION_WINDOW_CLOSED, "activation window is closed");
    }

    if (user1Id.equals(userId)) {
      if (user1ActivatedAt == null) {
        user1ActivatedAt = now;
      }
    } else if (user2Id.equals(userId)) {
      if (user2ActivatedAt == null) {
        user2ActivatedAt = now;
      }
    } else {
      throw new DomainException(ErrorCode.SESSION_NOT_PARTICIPANT, "user is not participant of this session");
    }

    if (user1ActivatedAt != null && user2ActivatedAt != null) {
      status = SessionStatus.ACTIVE;
      if (actualStartTime == null) {
        actualStartTime = now;
      }
    }
  }

  public void cancel(UUID userId, String reason, Instant now) {
    if (status == SessionStatus.CANCELLED) {
      return;
    }
    if (status != SessionStatus.PENDING) {
      throw new DomainException(ErrorCode.SESSION_INVALID_TRANSITION, "only PENDING can be cancelled");
    }
    assertParticipant(userId);

    status = SessionStatus.CANCELLED;
    cancellationReason = reason;
    cancelledBy = userId;
    actualEndTime = now;
  }

  public void complete(UUID userId, Instant now, SessionTrackingStats stats) {
    if (status == SessionStatus.COMPLETED) {
      return;
    }
    if (status != SessionStatus.ACTIVE) {
      throw new DomainException(ErrorCode.SESSION_INVALID_TRANSITION, "only ACTIVE can be completed");
    }
    assertParticipant(userId);

    long effectiveDuration = stats.totalDuration();
    if (effectiveDuration < MIN_PHYSICAL_DURATION.getSeconds()) {
      throw new DomainException(ErrorCode.SESSION_MIN_DURATION_NOT_REACHED, "minimum duration not reached");
    }

    totalDistance = stats.totalDistance();
    totalDuration = effectiveDuration;
    status = SessionStatus.COMPLETED;
    actualEndTime = now;
  }

  public void abort(UUID userId, AbortReason reason, Instant now) {
    if (status == SessionStatus.ABORTED) {
      return;
    }
    if (status != SessionStatus.ACTIVE) {
      throw new DomainException(ErrorCode.SESSION_INVALID_TRANSITION, "only ACTIVE can be aborted");
    }
    assertParticipant(userId);

    abortReason = reason;
    status = SessionStatus.ABORTED;
    actualEndTime = now;
  }

  public boolean systemProcessActivationWindow(Instant now) {
    if (status != SessionStatus.PENDING) {
      return false;
    }
    if (now.isBefore(scheduledStartTime.plus(LATE_GRACE))) {
      return false;
    }

    boolean p1 = user1ActivatedAt != null;
    boolean p2 = user2ActivatedAt != null;

    status = (p1 ^ p2) ? SessionStatus.NO_SHOW : SessionStatus.CANCELLED;
    actualEndTime = now;
    return true;
  }

  public boolean systemForceComplete(Instant now) {
    if (status != SessionStatus.ACTIVE || actualStartTime == null) {
      return false;
    }
    if (now.isBefore(actualStartTime.plus(MAX_PHYSICAL_DURATION))) {
      return false;
    }

    status = SessionStatus.COMPLETED;
    actualEndTime = now;
    if (totalDuration == 0) {
      totalDuration = Duration.between(actualStartTime, now).toSeconds();
    }
    return true;
  }

  public void updateTrackingSummary(SessionTrackingStats stats) {
    if (status != SessionStatus.ACTIVE && status != SessionStatus.COMPLETED) {
      throw new DomainException(ErrorCode.SESSION_INVALID_TRANSITION,
          "tracking only allowed for ACTIVE or COMPLETED sessions");
    }
    totalDistance = stats.totalDistance();
    totalDuration = stats.totalDuration();
  }

  private void assertNotTerminal() {
    if (status == SessionStatus.COMPLETED
        || status == SessionStatus.CANCELLED
        || status == SessionStatus.NO_SHOW
        || status == SessionStatus.ABORTED) {
      throw new DomainException(ErrorCode.SESSION_INVALID_TRANSITION, "session is terminal");
    }
  }

  private void assertParticipant(UUID userId) {
    if (!user1Id.equals(userId) && !user2Id.equals(userId)) {
      throw new DomainException(ErrorCode.SESSION_NOT_PARTICIPANT, "user is not participant of this session");
    }
  }

  public UUID getSessionId() {
    return sessionId;
  }

  public UUID getUser1Id() {
    return user1Id;
  }

  public UUID getUser2Id() {
    return user2Id;
  }

  public Instant getScheduledStartTime() {
    return scheduledStartTime;
  }

  public Instant getScheduledEndTime() {
    return scheduledEndTime;
  }

  public Instant getUser1ActivatedAt() {
    return user1ActivatedAt;
  }

  public Instant getUser2ActivatedAt() {
    return user2ActivatedAt;
  }

  public Instant getActualStartTime() {
    return actualStartTime;
  }

  public Instant getActualEndTime() {
    return actualEndTime;
  }

  public SessionStatus getStatus() {
    return status;
  }

  public BigDecimal getTotalDistance() {
    return totalDistance;
  }

  public long getTotalDuration() {
    return totalDuration;
  }

  public String getCancellationReason() {
    return cancellationReason;
  }

  public UUID getCancelledBy() {
    return cancelledBy;
  }

  public AbortReason getAbortReason() {
    return abortReason;
  }

  public long getVersion() {
    return version;
  }
}
