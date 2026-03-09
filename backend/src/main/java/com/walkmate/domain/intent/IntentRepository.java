package com.walkmate.domain.intent;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * PROTOTYPE: Intent Repository Interface
 * Defines db.sql INVARIANT 2: No overlapping OPEN intents for the same user.
 */
public interface IntentRepository {
    
    void save(WalkIntent intent);
    
    Optional<WalkIntent> findById(UUID id);
    
    /**
     * Translated from db.sql note:
     * 🔴 INVARIANT 2: No overlapping OPEN intents for same user
     * Application must check: EXISTS (SELECT 1 FROM walk_intent WHERE user_id = ? AND status = 'OPEN' 
     * AND time_window_start < ? AND time_window_end > ?)
     */
    boolean hasOverlappingOpenIntent(UUID userId, LocalDateTime newStart, LocalDateTime newEnd);
}
