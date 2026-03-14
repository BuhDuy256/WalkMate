package com.walkmate.domain.valueobject;

import com.walkmate.domain.intent.DomainException;
import com.walkmate.infrastructure.exception.ErrorCode;

import java.util.List;

public record MatchingConstraints(
    Integer minAge,
    Integer maxAge,
    String genderPreference,
    List<String> tagsPreference
) {
  public MatchingConstraints {
    if (minAge != null && minAge < 13) {
      throw new DomainException(ErrorCode.INTENT_INVALID_TRANSITION, "minAge cannot be less than 13");
    }
    if (minAge != null && maxAge != null && minAge > maxAge) {
      throw new DomainException(ErrorCode.INTENT_INVALID_TRANSITION, "minAge cannot be greater than maxAge");
    }
  }
}
