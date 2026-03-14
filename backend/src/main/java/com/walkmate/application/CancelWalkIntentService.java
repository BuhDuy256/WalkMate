package com.walkmate.application;

import com.walkmate.domain.intent.IntentRepository;
import com.walkmate.domain.intent.WalkIntent;
import com.walkmate.infrastructure.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CancelWalkIntentService {
  private final IntentRepository intentRepository;

  @Transactional
  public WalkIntent execute(UUID userId, UUID intentId) {
    WalkIntent intent = intentRepository.findById(intentId)
        .orElseThrow(() -> new ResourceNotFoundException("Intent not found"));

    if (!intent.getUserId().equals(userId)) {
      throw new ResourceNotFoundException("Intent not found for this user");
    }

    intent.cancel();

    return intentRepository.save(intent);
  }
}
