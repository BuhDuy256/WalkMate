package com.walkmate.domain.session;

import java.util.Optional;
import java.util.UUID;
import java.util.List;
import java.time.LocalDateTime;

/**
 * PROTOTYPE: Session Repository Interface.
 * Framework-agnostic. Implementations live in Infrastructure (JpaSessionRepository).
 */
public interface SessionRepository {
    
    Optional<WalkSession> findById(UUID id);
    
    void save(WalkSession session);

    // Used by Auto-Complete Jobs
    List<WalkSession> findByStatusAndScheduledStartTimeBefore(SessionStatus status, LocalDateTime time);
    
    // Used by Cross-Aggregate Domain Service to check invariant: "No Time Window Overlap" 
    boolean hasOverlappingSession(UUID userId, LocalDateTime start, LocalDateTime end, List<SessionStatus> statusList);
}
