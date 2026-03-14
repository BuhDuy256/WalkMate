package com.walkmate.infrastructure.repository;

import com.walkmate.domain.session.entity.WalkSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface JpaSessionRepository extends JpaRepository<WalkSession, UUID> {

    @Query(value = "SELECT * FROM walk_session WHERE status = 'PENDING' " +
                   "AND scheduled_start_time + interval '10 minutes' < NOW() " +
                   "FOR UPDATE SKIP LOCKED LIMIT :limit", nativeQuery = true)
    List<WalkSession> findExpiredPendingSessionsForUpdate(@Param("limit") int limit);

    @Query(value = "SELECT * FROM walk_session WHERE status = 'ACTIVE' " +
                   "AND actual_start_time < NOW() - interval '4 hours' " +
                   "FOR UPDATE SKIP LOCKED LIMIT :limit", nativeQuery = true)
    List<WalkSession> findExpiredActiveSessionsForUpdate(@Param("limit") int limit);
}
