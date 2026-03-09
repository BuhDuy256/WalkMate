package com.walkmate.infrastructure.repository;

import com.walkmate.domain.session.SessionStatus;
import com.walkmate.domain.session.WalkSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA Interface for WalkSession.
 * Not exposed to Domain layer directly to keep Domain pure.
 */
public interface SpringDataSessionRepository extends JpaRepository<WalkSession, UUID> {

    List<WalkSession> findByStatusAndScheduledStartTimeBefore(SessionStatus status, LocalDateTime time);

    @Query("SELECT CASE WHEN COUNT(s) > 0 THEN true ELSE false END " +
           "FROM WalkSession s " +
           "WHERE (s.participant1 = :userId OR s.participant2 = :userId) " +
           "AND s.status IN :statuses " +
           "AND (s.scheduledStartTime < :end AND s.scheduledEndTime > :start)")
    boolean hasOverlappingSession(
            @Param("userId") UUID userId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("statuses") List<SessionStatus> statuses
    );
}
