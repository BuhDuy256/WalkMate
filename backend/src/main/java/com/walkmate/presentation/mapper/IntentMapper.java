package com.walkmate.presentation.mapper;

import com.walkmate.domain.intent.WalkIntent;
import com.walkmate.presentation.dto.response.IntentResponse;
import org.springframework.stereotype.Component;

@Component
public class IntentMapper {
  public IntentResponse toResponse(WalkIntent intent) {
    if (intent == null) return null;
    return new IntentResponse(
        intent.getIntentId(),
        intent.getUserId(),
        intent.getLocation().lat(),
        intent.getLocation().lng(),
        intent.getTimeWindow().start(),
        intent.getTimeWindow().end(),
        intent.getPurpose(),
        intent.getMatchingConstraints(),
        intent.getStatus(),
        intent.getCreatedAt(),
        intent.getExpiresAt()
    );
  }
}
