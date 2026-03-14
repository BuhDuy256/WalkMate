package com.walkmate.application;

import com.walkmate.domain.intent.IntentRepository;
import com.walkmate.domain.intent.WalkIntent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ExpireWalkIntentJobService {
  private final IntentRepository intentRepository;

  @Transactional
  public int execute(int batchSize) {
    Instant now = Instant.now();
    
    // Pessimistic lock SKIP LOCKED ensures safety (I-7)
    List<WalkIntent> expiredIntents = intentRepository.findExpiredOpenIntentsForUpdate(now, batchSize);

    for (WalkIntent intent : expiredIntents) {
      intent.expire(now);
      intentRepository.save(intent);
    }
    
    return expiredIntents.size();
  }
}
