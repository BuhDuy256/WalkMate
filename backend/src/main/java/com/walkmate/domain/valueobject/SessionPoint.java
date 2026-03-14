package com.walkmate.domain.valueobject;

public record SessionPoint(int pointOrder, double latitude, double longitude, long time) {
  public SessionPoint {
    if (pointOrder < 0) {
      throw new IllegalArgumentException("pointOrder must be >= 0");
    }
    if (latitude < -90 || latitude > 90) {
      throw new IllegalArgumentException("latitude out of range");
    }
    if (longitude < -180 || longitude > 180) {
      throw new IllegalArgumentException("longitude out of range");
    }
    if (time < 0) {
      throw new IllegalArgumentException("time must be >= 0");
    }
  }
}
