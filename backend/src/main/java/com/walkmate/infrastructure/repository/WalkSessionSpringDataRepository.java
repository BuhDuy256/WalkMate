package com.walkmate.infrastructure.repository;

import com.walkmate.infrastructure.repository.entity.WalkSessionJpaEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WalkSessionSpringDataRepository extends JpaRepository<WalkSessionJpaEntity, UUID> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select s from WalkSessionJpaEntity s where s.sessionId = :sessionId")
  Optional<WalkSessionJpaEntity> findByIdForUpdate(@Param("sessionId") UUID sessionId);

  @Query(value = """
      SELECT * FROM walk_session
      WHERE status = 'PENDING'
      AND scheduled_start_time + interval '10 minutes' < NOW()
      FOR UPDATE SKIP LOCKED
      LIMIT :limit
      """, nativeQuery = true)
  List<WalkSessionJpaEntity> findExpiredPendingSessionsForUpdate(@Param("limit") int limit);

  @Query(value = """
      SELECT * FROM walk_session
      WHERE status = 'ACTIVE'
      AND actual_start_time < NOW() - interval '4 hours'
      FOR UPDATE SKIP LOCKED
      LIMIT :limit
      """, nativeQuery = true)
  List<WalkSessionJpaEntity> findExpiredActiveSessionsForUpdate(@Param("limit") int limit);
}
