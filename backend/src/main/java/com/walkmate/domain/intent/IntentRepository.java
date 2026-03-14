package com.walkmate.domain.intent;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IntentRepository {
  WalkIntent save(WalkIntent intent);
  
  Optional<WalkIntent> findById(UUID id);
  
  List<WalkIntent> findByUserIdAndStatus(UUID userId, IntentStatus status);
  
  /**
   * Fetches intents that are OPEN and their time window end has passed.
   * This query must use pessimistic row locking (e.g. SELECT FOR UPDATE SKIP LOCKED)
   * to prevent contention between expire cronjobs and matching consumptions.
   */
  List<WalkIntent> findExpiredOpenIntentsForUpdate(Instant now, int limit);
}
