package com.walkmate.application;

import com.walkmate.domain.session.SessionRepository;
import com.walkmate.domain.session.SessionStatus;
import com.walkmate.domain.session.WalkSession;
import com.walkmate.domain.valueobject.SessionPoint;
import org.junit.jupiter.api.Test;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;

class ActivateWalkSessionUseCaseConcurrencyTest {

  @Test
  void shouldPropagateOptimisticLockConflict() {
    Instant now = Instant.parse("2026-03-14T07:00:00Z");
    WalkSession session = new WalkSession(
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        now,
        now.plusSeconds(1800),
        null,
        null,
        null,
        null,
        SessionStatus.PENDING,
        BigDecimal.ZERO,
        0,
        null,
        null,
        null,
        0);

    SessionRepository repo = new SessionRepository() {
      @Override
      public Optional<WalkSession> findById(UUID sessionId) {
        return Optional.of(session);
      }

      @Override
      public Optional<WalkSession> findByIdForUpdate(UUID sessionId) {
        return Optional.of(session);
      }

      @Override
      public WalkSession save(WalkSession session) {
        throw new ObjectOptimisticLockingFailureException(WalkSession.class, session.getSessionId());
      }

      @Override
      public List<WalkSession> findExpiredPendingSessionsForUpdate(int limit) {
        return List.of();
      }

      @Override
      public List<WalkSession> findExpiredActiveSessionsForUpdate(int limit) {
        return List.of();
      }

      @Override
      public int appendSessionPoints(UUID sessionId, List<SessionPoint> points) {
        return 0;
      }
    };

    ActivateWalkSessionService useCase = new ActivateWalkSessionService(repo, Clock.fixed(now, ZoneOffset.UTC));

    assertThrows(ObjectOptimisticLockingFailureException.class,
        () -> useCase.execute(session.getUser1Id(), session.getSessionId()));
  }
}
