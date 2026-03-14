package com.walkmate.domain.valueobject;

import com.walkmate.domain.intent.DomainException;
import com.walkmate.infrastructure.exception.ErrorCode;

public record LocationSnapshot(double lat, double lng) {
  public LocationSnapshot {
    if (lat < -90 || lat > 90 || lng < -180 || lng > 180) {
      throw new DomainException(ErrorCode.INTENT_INVALID_COORDINATE, "latitude or longitude bounds invalid");
    }
  }
}
