package com.walkmate.application;

import com.walkmate.domain.session.SessionRepository;
import com.walkmate.domain.session.SessionStatus;
import com.walkmate.domain.session.WalkSession;
import com.walkmate.domain.valueobject.SessionPoint;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProcessSessionActivationWindowUseCaseTest {

  @Test
  void shouldUpdateExpiredPendingSessions() {
    Instant now = Instant.parse("2026-03-14T08:00:00Z");
    InMemoryRepository repo = new InMemoryRepository();
    repo.sessions.add(samplePending(now.minusSeconds(20 * 60)));

    ProcessSessionActivationWindowService useCase = new ProcessSessionActivationWindowService(
        repo,
        Clock.fixed(now, ZoneOffset.UTC));

    int updated = useCase.execute(100);

    assertEquals(1, updated);
    assertEquals(SessionStatus.CANCELLED, repo.sessions.get(0).getStatus());
  }

  private WalkSession samplePending(Instant start) {
    return new WalkSession(
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        start,
        start.plusSeconds(30 * 60),
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
  }

  private static class InMemoryRepository implements SessionRepository {
    private final List<WalkSession> sessions = new ArrayList<>();

    @Override
    public Optional<WalkSession> findById(UUID sessionId) {
      return sessions.stream().filter(s -> s.getSessionId().equals(sessionId)).findFirst();
    }

    @Override
    public Optional<WalkSession> findByIdForUpdate(UUID sessionId) {
      return findById(sessionId);
    }

    @Override
    public WalkSession save(WalkSession session) {
      return session;
    }

    @Override
    public List<WalkSession> findExpiredPendingSessionsForUpdate(int limit) {
      return sessions.stream().filter(s -> s.getStatus() == SessionStatus.PENDING).limit(limit).toList();
    }

    @Override
    public List<WalkSession> findExpiredActiveSessionsForUpdate(int limit) {
      return List.of();
    }

    @Override
    public int appendSessionPoints(UUID sessionId, List<SessionPoint> points) {
      return points.size();
    }
  }
}
