package com.walkmate.domain.intent;

import com.walkmate.domain.valueobject.LocationSnapshot;
import com.walkmate.domain.valueobject.MatchingConstraints;
import com.walkmate.domain.valueobject.TimeWindow;
import com.walkmate.infrastructure.exception.ErrorCode;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class WalkIntent {
  private static final Duration CREATION_BUFFER = Duration.ofMinutes(0); // Optional buffer for I-6

  private final UUID intentId;
  private final UUID userId;
  private final LocationSnapshot location;
  private final TimeWindow timeWindow;
  private final WalkPurpose purpose;
  private final MatchingConstraints matchingConstraints;
  private IntentStatus status;
  private final Instant createdAt;
  private Instant expiresAt;
  private long version;

  public WalkIntent(
      UUID intentId,
      UUID userId,
      LocationSnapshot location,
      TimeWindow timeWindow,
      WalkPurpose purpose,
      MatchingConstraints matchingConstraints,
      IntentStatus status,
      Instant createdAt,
      Instant expiresAt,
      long version) {
    this.intentId = Objects.requireNonNull(intentId);
    this.userId = Objects.requireNonNull(userId);
    this.location = Objects.requireNonNull(location);
    this.timeWindow = Objects.requireNonNull(timeWindow);
    this.purpose = Objects.requireNonNull(purpose);
    this.matchingConstraints = matchingConstraints;
    this.status = Objects.requireNonNull(status);
    this.createdAt = Objects.requireNonNull(createdAt);
    this.expiresAt = Objects.requireNonNull(expiresAt);
    this.version = version;
  }

  public static WalkIntent create(
      UUID intentId,
      UUID userId,
      LocationSnapshot location,
      TimeWindow timeWindow,
      WalkPurpose purpose,
      MatchingConstraints matchingConstraints,
      Instant now) {
    
    // Invariant I-6: Future time window check
    if (timeWindow.start().isBefore(now.plus(CREATION_BUFFER))) {
       throw new DomainException(ErrorCode.INTENT_INVALID_TIME_WINDOW, "start time must be in the future");
    }

    return new WalkIntent(
        intentId,
        userId,
        location,
        timeWindow,
        purpose,
        matchingConstraints,
        IntentStatus.OPEN,
        now,
        timeWindow.end(),
        0L
    );
  }

  public void consume() {
    if (status != IntentStatus.OPEN) {
      throw new DomainException(ErrorCode.INTENT_INVALID_TRANSITION, "only OPEN intents can be consumed");
    }
    status = IntentStatus.CONSUMED;
  }

  public void cancel() {
    if (status != IntentStatus.OPEN) {
      throw new DomainException(ErrorCode.INTENT_INVALID_TRANSITION, "only OPEN intents can be cancelled");
    }
    status = IntentStatus.CANCELLED;
  }

  public void expire(Instant now) {
    if (status != IntentStatus.OPEN) {
      throw new DomainException(ErrorCode.INTENT_INVALID_TRANSITION, "only OPEN intents can be expired");
    }
    if (now.isBefore(timeWindow.end())) {
      throw new DomainException(ErrorCode.INTENT_INVALID_TRANSITION, "intent time window has not completely passed");
    }
    status = IntentStatus.EXPIRED;
  }

  public UUID getIntentId() {
    return intentId;
  }

  public UUID getUserId() {
    return userId;
  }

  public LocationSnapshot getLocation() {
    return location;
  }

  public TimeWindow getTimeWindow() {
    return timeWindow;
  }

  public WalkPurpose getPurpose() {
    return purpose;
  }

  public MatchingConstraints getMatchingConstraints() {
    return matchingConstraints;
  }

  public IntentStatus getStatus() {
    return status;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public long getVersion() {
    return version;
  }
}
