package com.walkmate.infrastructure.repository;

import com.walkmate.domain.intent.IntentStatus;
import com.walkmate.infrastructure.repository.entity.WalkIntentJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface WalkIntentSpringDataRepository extends JpaRepository<WalkIntentJpaEntity, UUID> {
    
    List<WalkIntentJpaEntity> findByUserIdAndStatus(UUID userId, IntentStatus status);

    @Query(value = """
        SELECT * FROM walk_intent
        WHERE status = 'OPEN'
        AND time_window_end <= :now
        FOR UPDATE SKIP LOCKED
        LIMIT :limit
        """, nativeQuery = true)
    List<WalkIntentJpaEntity> findExpiredOpenIntentsForUpdate(@Param("now") Instant now, @Param("limit") int limit);
}
