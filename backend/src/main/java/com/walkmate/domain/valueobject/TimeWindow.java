package com.walkmate.domain.valueobject;

import com.walkmate.domain.intent.DomainException;
import com.walkmate.infrastructure.exception.ErrorCode;

import java.time.Instant;
import java.util.Objects;

public record TimeWindow(Instant start, Instant end) {
  public TimeWindow {
    Objects.requireNonNull(start, "start time must not be null");
    Objects.requireNonNull(end, "end time must not be null");
    if (!end.isAfter(start)) {
      throw new DomainException(ErrorCode.INTENT_INVALID_TIME_WINDOW, "end time must be after start time");
    }
  }

  public boolean isExpired(Instant now) {
    return !now.isBefore(end);
  }
}
