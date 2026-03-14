package com.walkmate.infrastructure.repository;

import com.walkmate.domain.session.AbortReason;
import com.walkmate.domain.session.SessionRepository;
import com.walkmate.domain.session.WalkSession;
import com.walkmate.domain.valueobject.SessionPoint;
import com.walkmate.infrastructure.repository.entity.SessionPointJpaEntity;
import com.walkmate.infrastructure.repository.entity.WalkSessionJpaEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JpaSessionRepository implements SessionRepository {
  private final WalkSessionSpringDataRepository walkSessionRepository;
  private final SessionPointSpringDataRepository sessionPointRepository;

  @Override
  public Optional<WalkSession> findById(UUID sessionId) {
    return walkSessionRepository.findById(sessionId).map(this::toDomain);
  }

  @Override
  public Optional<WalkSession> findByIdForUpdate(UUID sessionId) {
    return walkSessionRepository.findByIdForUpdate(sessionId).map(this::toDomain);
  }

  @Override
  public WalkSession save(WalkSession session) {
    WalkSessionJpaEntity saved = walkSessionRepository.save(toJpa(session));
    return toDomain(saved);
  }

  @Override
  public List<WalkSession> findExpiredPendingSessionsForUpdate(int limit) {
    return walkSessionRepository.findExpiredPendingSessionsForUpdate(limit).stream().map(this::toDomain).toList();
  }

  @Override
  public List<WalkSession> findExpiredActiveSessionsForUpdate(int limit) {
    return walkSessionRepository.findExpiredActiveSessionsForUpdate(limit).stream().map(this::toDomain).toList();
  }

  @Override
  public int appendSessionPoints(UUID sessionId, List<SessionPoint> points) {
    List<SessionPointJpaEntity> entities = points.stream().map(point -> {
      SessionPointJpaEntity e = new SessionPointJpaEntity();
      e.setSessionId(sessionId);
      e.setPointOrder(point.pointOrder());
      e.setLatitude(point.latitude());
      e.setLongitude(point.longitude());
      e.setTime(point.time());
      return e;
    }).toList();

    return sessionPointRepository.saveAll(entities).size();
  }

  private WalkSession toDomain(WalkSessionJpaEntity entity) {
    return new WalkSession(
        entity.getSessionId(),
        entity.getUser1Id(),
        entity.getUser2Id(),
        entity.getScheduledStartTime(),
        entity.getScheduledEndTime(),
        entity.getUser1ActivatedAt(),
        entity.getUser2ActivatedAt(),
        entity.getActualStartTime(),
        entity.getActualEndTime(),
        entity.getStatus(),
        entity.getTotalDistance() == null ? BigDecimal.ZERO : entity.getTotalDistance(),
        entity.getTotalDuration(),
        entity.getCancellationReason(),
        entity.getCancelledBy(),
        entity.getAbortReason(),
        entity.getVersion());
  }

  private WalkSessionJpaEntity toJpa(WalkSession session) {
    WalkSessionJpaEntity entity = new WalkSessionJpaEntity();
    entity.setSessionId(session.getSessionId());
    entity.setUser1Id(session.getUser1Id());
    entity.setUser2Id(session.getUser2Id());
    entity.setScheduledStartTime(session.getScheduledStartTime());
    entity.setScheduledEndTime(session.getScheduledEndTime());
    entity.setUser1ActivatedAt(session.getUser1ActivatedAt());
    entity.setUser2ActivatedAt(session.getUser2ActivatedAt());
    entity.setActualStartTime(session.getActualStartTime());
    entity.setActualEndTime(session.getActualEndTime());
    entity.setStatus(session.getStatus());
    entity.setTotalDistance(session.getTotalDistance());
    entity.setTotalDuration(session.getTotalDuration());
    entity.setCancellationReason(session.getCancellationReason());
    entity.setCancelledBy(session.getCancelledBy());
    entity
        .setAbortReason(session.getAbortReason() == null ? null : AbortReason.valueOf(session.getAbortReason().name()));
    entity.setVersion(session.getVersion());
    return entity;
  }
}
