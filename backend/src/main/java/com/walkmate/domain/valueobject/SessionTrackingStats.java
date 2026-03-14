package com.walkmate.domain.valueobject;

import java.math.BigDecimal;

public record SessionTrackingStats(BigDecimal totalDistance, long totalDuration) {
  public SessionTrackingStats {
    if (totalDistance == null || totalDistance.signum() < 0) {
      throw new IllegalArgumentException("totalDistance must be >= 0");
    }
    if (totalDuration < 0) {
      throw new IllegalArgumentException("totalDuration must be >= 0");
    }
  }
}
