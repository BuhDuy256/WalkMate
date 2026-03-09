package com.walkmate.infrastructure.repository;

import com.walkmate.domain.intent.WalkIntent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Spring Data JPA Interface for WalkIntent.
 */
public interface SpringDataIntentRepository extends JpaRepository<WalkIntent, UUID> {

    @Query("SELECT CASE WHEN COUNT(i) > 0 THEN true ELSE false END " +
           "FROM WalkIntent i " +
           "WHERE i.userId = :userId " +
           "AND i.status = 'OPEN' " +
           "AND (i.timeWindowStart < :end AND i.timeWindowEnd > :start)")
    boolean hasOverlappingOpenIntent(
            @Param("userId") UUID userId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );
}
