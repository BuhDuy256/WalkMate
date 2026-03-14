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
public class ConsumeWalkIntentService {
  private final IntentRepository intentRepository;

  @Transactional
  public WalkIntent execute(UUID intentId) {
    WalkIntent intent = intentRepository.findById(intentId)
        .orElseThrow(() -> new ResourceNotFoundException("Intent not found"));

    intent.consume();

    return intentRepository.save(intent);
  }
}
